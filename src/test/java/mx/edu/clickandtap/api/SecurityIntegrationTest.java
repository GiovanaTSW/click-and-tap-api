package mx.edu.clickandtap.api;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 401 (not logged in) versus 403 (logged in without permission), and token tampering. */
class SecurityIntegrationTest extends IntegrationTestBase {

    private record Endpoint(HttpMethod method, String path) {}

    private static final List<Endpoint> NEED_A_TOKEN = List.of(
            new Endpoint(GET, "/api/auth/me"),
            new Endpoint(GET, "/api/orders"),
            new Endpoint(POST, "/api/orders"),
            new Endpoint(GET, "/api/orders/1"),
            new Endpoint(GET, "/api/experiences"),
            new Endpoint(POST, "/api/experiences"),
            new Endpoint(PUT, "/api/experiences/1"),
            new Endpoint(DELETE, "/api/experiences/1"),
            new Endpoint(GET, "/api/admin/orders"),
            new Endpoint(GET, "/api/admin/products"),
            new Endpoint(POST, "/api/products"),
            new Endpoint(PATCH, "/api/products/1/stock"));

    private static final List<Endpoint> ADMIN_ONLY = List.of(
            new Endpoint(GET, "/api/admin/orders"),
            new Endpoint(GET, "/api/admin/products"),
            new Endpoint(GET, "/api/admin/products/1"),
            new Endpoint(PATCH, "/api/admin/orders/1/status"),
            new Endpoint(POST, "/api/products"),
            new Endpoint(PUT, "/api/products/1"),
            new Endpoint(PATCH, "/api/products/1/stock"),
            new Endpoint(DELETE, "/api/products/1"));

    @Test
    void publicEndpointsDoNotAskForAToken() throws Exception {
        assertThat(status(call(GET, "/api/products", null, null))).isEqualTo(200);
        assertThat(status(call(GET, "/api/public/experiences/does-not-exist", null, null))).isEqualTo(404);
        assertThat(status(call(GET, "/favicon.ico", null, null))).isEqualTo(404);
        assertThat(status(call(GET, "/v3/api-docs", null, null))).isEqualTo(200);
    }

    @Test
    void onlyTheHealthCheckIsPublicAmongTheActuatorEndpoints() throws Exception {
        MvcResult health = call(GET, "/actuator/health", null, null);

        assertThat(status(health)).isEqualTo(200);
        assertThat(str(health, "$.status")).isEqualTo("UP");
        assertThat(body(health)).as("no component details are leaked").doesNotContain("postgres").doesNotContain("jdbc");
        assertThat(status(call(GET, "/actuator/env", null, null))).isEqualTo(401);
        assertThat(status(call(GET, "/actuator/beans", null, null))).isEqualTo(401);
        assertThat(status(call(GET, "/actuator/env", adminToken(), null))).as("not exposed at all").isEqualTo(404);
    }

    @Test
    void protectedEndpointsAnswer401WithTheStandardBodyWhenThereIsNoToken() throws Exception {
        for (Endpoint e : NEED_A_TOKEN) {
            MvcResult result = call(e.method(), e.path(), null, null);

            assertThat(status(result)).as("%s %s", e.method(), e.path()).isEqualTo(401);
            assertThat(integer(result, "$.status")).isEqualTo(401);
            assertThat(str(result, "$.message")).isEqualTo("Authentication required");
        }
    }

    @Test
    void customersGet403OnEveryAdminEndpoint() throws Exception {
        Customer customer = registerCustomer();

        for (Endpoint e : ADMIN_ONLY) {
            MvcResult result = call(e.method(), e.path(), customer.token(), null);

            assertThat(status(result)).as("%s %s", e.method(), e.path()).isEqualTo(403);
            assertThat(integer(result, "$.status")).isEqualTo(403);
            assertThat(str(result, "$.message")).contains("permission");
        }
    }

    @Test
    void adminsReachTheAdminEndpoints() throws Exception {
        assertThat(status(call(GET, "/api/admin/orders", adminToken(), null))).isEqualTo(200);
        assertThat(status(call(GET, "/api/admin/products", adminToken(), null))).isEqualTo(200);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejectedEvenIfItClaimsToBeAdmin() throws Exception {
        SecretKey attackerKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject(ADMIN_EMAIL).claim("uid", 1).claim("role", "ADMIN")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(attackerKey).compact();

        MvcResult result = call(GET, "/api/admin/orders", forged, null);

        assertThat(status(result)).isEqualTo(401);
    }

    @Test
    void anUnsignedTokenIsRejected() throws Exception {
        String unsigned = Jwts.builder()
                .subject(ADMIN_EMAIL).claim("uid", 1).claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .compact();

        assertThat(status(call(GET, "/api/admin/orders", unsigned, null))).isEqualTo(401);
    }

    @Test
    void aTokenWithATamperedPayloadIsRejected() throws Exception {
        Customer customer = registerCustomer();
        String[] parts = customer.token().split("\\.");
        // Flip one character of the payload: the signature no longer matches.
        char first = parts[1].charAt(0);
        String tamperedPayload = (first == 'A' ? 'B' : 'A') + parts[1].substring(1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(status(call(GET, "/api/auth/me", tampered, null))).isEqualTo(401);
    }

    @Test
    void everyErrorLeavesTheApiInTheSameShape() throws Exception {
        Customer customer = registerCustomer();
        List<MvcResult> errors = List.of(
                call(GET, "/api/nothing-here", customer.token(), null),                 // 404
                call(DELETE, "/api/orders", customer.token(), null),                    // 405
                call(GET, "/api/products?type=SPACESHIP", null, null),                  // 400 bad enum
                call(GET, "/api/orders/not-a-number", customer.token(), null),          // 400 bad id
                call(POST, "/api/orders", customer.token(), "{not json"));              // 400 bad json
        List<Integer> expected = List.of(404, 405, 400, 400, 400);

        for (int i = 0; i < errors.size(); i++) {
            MvcResult result = errors.get(i);
            assertThat(status(result)).as("error #%d", i).isEqualTo(expected.get(i));
            assertThat(integer(result, "$.status")).isEqualTo(expected.get(i));
            assertThat(str(result, "$.message")).isNotBlank();
        }
    }
}
