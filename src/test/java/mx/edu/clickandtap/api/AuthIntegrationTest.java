package mx.edu.clickandtap.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registerReturnsATokenAndNeverExposesThePasswordHash() throws Exception {
        String email = uniqueEmail();

        MvcResult result = call(POST, "/api/auth/register", null, registerJson("Ana", email, CUSTOMER_PASSWORD));

        assertThat(status(result)).isEqualTo(201);
        assertThat(str(result, "$.token")).isNotBlank();
        assertThat(str(result, "$.user.email")).isEqualTo(email);
        assertThat(str(result, "$.user.role")).isEqualTo("CUSTOMER");
        assertThat(body(result)).doesNotContainIgnoringCase("passwordHash").doesNotContain(CUSTOMER_PASSWORD);
    }

    @Test
    void emailIsStoredInLowerCase() throws Exception {
        String email = uniqueEmail();

        MvcResult result = call(POST, "/api/auth/register", null,
                registerJson("Ana", email.toUpperCase().replace("@EXAMPLE.COM", "@Example.COM"), CUSTOMER_PASSWORD));

        assertThat(status(result)).isEqualTo(201);
        assertThat(str(result, "$.user.email")).isEqualTo(email);
    }

    @Test
    void aClientCannotChooseItsOwnRoleAtRegistration() throws Exception {
        String json = """
                {"name":"Mallory","email":"%s","password":"%s","role":"ADMIN"}""".formatted(uniqueEmail(), CUSTOMER_PASSWORD);

        MvcResult result = call(POST, "/api/auth/register", null, json);

        assertThat(status(result)).isEqualTo(201);
        assertThat(str(result, "$.user.role")).isEqualTo("CUSTOMER");
    }

    @Test
    void duplicateEmailIsRejectedEvenWithDifferentCase() throws Exception {
        Customer existing = registerCustomer();

        MvcResult result = call(POST, "/api/auth/register", null,
                registerJson("Other", existing.email().toUpperCase(), CUSTOMER_PASSWORD));

        assertThat(status(result)).isEqualTo(400);
        assertThat(str(result, "$.message")).isEqualTo("Email is already registered");
    }

    @Test
    void invalidRegistrationDataGivesAClear400() throws Exception {
        MvcResult shortPassword = call(POST, "/api/auth/register", null, registerJson("Ana", uniqueEmail(), "123"));
        MvcResult badEmail = call(POST, "/api/auth/register", null, registerJson("Ana", "not-an-email", CUSTOMER_PASSWORD));
        MvcResult longName = call(POST, "/api/auth/register", null,
                registerJson("x".repeat(200), uniqueEmail(), CUSTOMER_PASSWORD));

        assertThat(status(shortPassword)).isEqualTo(400);
        assertThat(str(shortPassword, "$.message")).contains("password");
        assertThat(status(badEmail)).isEqualTo(400);
        assertThat(str(badEmail, "$.message")).contains("email");
        assertThat(status(longName)).isEqualTo(400);
        assertThat(str(longName, "$.message")).contains("name");
    }

    @Test
    void loginReturnsATokenForTheRightCredentials() throws Exception {
        Customer customer = registerCustomer();

        MvcResult result = call(POST, "/api/auth/login", null, loginJson(customer.email(), CUSTOMER_PASSWORD));

        assertThat(status(result)).isEqualTo(200);
        assertThat(str(result, "$.token")).isNotBlank();
        assertThat(lng(result, "$.user.id")).isEqualTo(customer.id());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        Customer customer = registerCustomer();

        MvcResult wrongPassword = call(POST, "/api/auth/login", null, loginJson(customer.email(), "wrong-password-1"));
        MvcResult unknownEmail = call(POST, "/api/auth/login", null, loginJson(uniqueEmail(), "wrong-password-1"));

        assertThat(status(wrongPassword)).isEqualTo(401);
        assertThat(status(unknownEmail)).isEqualTo(401);
        assertThat(body(wrongPassword)).isEqualTo(body(unknownEmail));
        assertThat(str(wrongPassword, "$.message")).isEqualTo("Invalid credentials");
    }

    @Test
    void meRequiresAValidToken() throws Exception {
        Customer customer = registerCustomer();

        MvcResult noToken = call(GET, "/api/auth/me", null, null);
        MvcResult garbage = call(GET, "/api/auth/me", "not.a.jwt", null);
        MvcResult valid = call(GET, "/api/auth/me", customer.token(), null);

        assertThat(status(noToken)).isEqualTo(401);
        assertThat(str(noToken, "$.message")).isEqualTo("Authentication required");
        assertThat(status(garbage)).isEqualTo(401);
        assertThat(status(valid)).isEqualTo(200);
        assertThat(str(valid, "$.email")).isEqualTo(customer.email());
    }

    @Test
    void malformedJsonIsA400WithTheStandardErrorShape() throws Exception {
        MvcResult result = call(POST, "/api/auth/login", null, "{not json");

        assertThat(status(result)).isEqualTo(400);
        assertThat(integer(result, "$.status")).isEqualTo(400);
        assertThat(str(result, "$.message")).isEqualTo("Malformed or invalid JSON request");
    }

    @Test
    void fiveFailedLoginsBlockThatAccountFromThatIpOnly() throws Exception {
        Customer customer = registerCustomer();
        String attackerIp = "192.0.2.77";

        for (int i = 0; i < 5; i++) {
            MvcResult attempt = callFrom(attackerIp, POST, "/api/auth/login", null,
                    loginJson(customer.email(), "wrong-password-1"));
            assertThat(status(attempt)).as("attempt %d", i + 1).isEqualTo(401);
        }

        MvcResult blocked = callFrom(attackerIp, POST, "/api/auth/login", null,
                loginJson(customer.email(), "wrong-password-1"));
        assertThat(status(blocked)).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(str(blocked, "$.message")).contains("Too many");

        // While blocked, even the right password is refused from that IP...
        MvcResult rightPasswordFromAttacker = callFrom(attackerIp, POST, "/api/auth/login", null,
                loginJson(customer.email(), CUSTOMER_PASSWORD));
        assertThat(status(rightPasswordFromAttacker)).isEqualTo(429);

        // ...but the real owner on another network is not locked out.
        MvcResult ownerElsewhere = callFrom("192.0.2.78", POST, "/api/auth/login", null,
                loginJson(customer.email(), CUSTOMER_PASSWORD));
        assertThat(status(ownerElsewhere)).isEqualTo(200);
    }

    @Test
    void registeringTooManyAccountsFromOneIpIsBlocked() throws Exception {
        String ip = "192.0.2.90";

        for (int i = 0; i < 10; i++) {
            MvcResult ok = callFrom(ip, POST, "/api/auth/register", null,
                    registerJson("Bot", uniqueEmail(), CUSTOMER_PASSWORD));
            assertThat(status(ok)).as("registration %d", i + 1).isEqualTo(201);
        }

        MvcResult blocked = callFrom(ip, POST, "/api/auth/register", null,
                registerJson("Bot", uniqueEmail(), CUSTOMER_PASSWORD));
        assertThat(status(blocked)).isEqualTo(429);
    }
}
