package mx.edu.clickandtap.api;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Base class for integration tests: the whole application (security filters, controllers, services,
 * Hibernate, Flyway) running against a real PostgreSQL 18 started by Testcontainers.
 *
 * - One container and one Spring context are shared by every subclass, so the suite starts them once.
 * - Because the database is shared, tests never assume it is empty: each one creates its own data
 *   with unique names and e-mails.
 * - Every request is sent from a different fake IP unless a test chooses one, so the login/register
 *   rate limiter does not interfere between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {
    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "admin-password-123";
    protected static final String CUSTOMER_PASSWORD = "password123";

    protected static final HttpMethod GET = HttpMethod.GET;
    protected static final HttpMethod POST = HttpMethod.POST;
    protected static final HttpMethod PUT = HttpMethod.PUT;
    protected static final HttpMethod PATCH = HttpMethod.PATCH;
    protected static final HttpMethod DELETE = HttpMethod.DELETE;

    /** Same major version as production. Set TEST_POSTGRES_IMAGE to use another image (e.g. one already downloaded). */
    private static final String POSTGRES_IMAGE = System.getenv().getOrDefault("TEST_POSTGRES_IMAGE", "postgres:18-alpine");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();
    private static volatile String cachedAdminToken;

    static {
        // Started once for the whole JVM; Testcontainers' Ryuk container removes it when the tests end.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    public record Customer(long id, String email, String token) {}

    // ---------- sending requests ----------

    /** Sends a request from a fresh fake IP. {@code token} and {@code body} may be null. */
    protected MvcResult call(HttpMethod method, String path, String token, String body) throws Exception {
        return callFrom(freshIp(), method, path, token, body);
    }

    protected MvcResult callFrom(String ip, HttpMethod method, String path, String token, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path).with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(builder).andReturn();
    }

    protected static String freshIp() {
        int n = IP_COUNTER.incrementAndGet();
        return "10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
    }

    // ---------- reading responses ----------

    protected static int status(MvcResult result) { return result.getResponse().getStatus(); }

    protected static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected static <T> T at(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(body(result), jsonPath);
    }

    protected static String str(MvcResult result, String jsonPath) throws Exception {
        return at(result, jsonPath);
    }

    protected static long lng(MvcResult result, String jsonPath) throws Exception {
        return ((Number) at(result, jsonPath)).longValue();
    }

    protected static int integer(MvcResult result, String jsonPath) throws Exception {
        return ((Number) at(result, jsonPath)).intValue();
    }

    protected static boolean bool(MvcResult result, String jsonPath) throws Exception {
        return (Boolean) at(result, jsonPath);
    }

    protected static double dbl(MvcResult result, String jsonPath) throws Exception {
        return ((Number) at(result, jsonPath)).doubleValue();
    }

    // ---------- test data ----------

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    protected static String registerJson(String name, String email, String password) {
        return """
                {"name":"%s","email":"%s","password":"%s"}""".formatted(name, email, password);
    }

    protected static String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    protected static String orderJson(long productId, int quantity) {
        return """
                {"shippingAddress":"Calle 1 #123","items":[{"productId":%d,"quantity":%d}]}"""
                .formatted(productId, quantity);
    }

    protected String adminToken() throws Exception {
        if (cachedAdminToken == null) {
            MvcResult login = call(POST, "/api/auth/login", null, loginJson(ADMIN_EMAIL, ADMIN_PASSWORD));
            cachedAdminToken = str(login, "$.token");
        }
        return cachedAdminToken;
    }

    protected Customer registerCustomer() throws Exception {
        String email = uniqueEmail();
        MvcResult result = call(POST, "/api/auth/register", null, registerJson("Test Customer", email, CUSTOMER_PASSWORD));
        return new Customer(lng(result, "$.user.id"), email, str(result, "$.token"));
    }

    /** Creates an active product through the admin API and returns its id. */
    protected long createProduct(String name, String price, int stock) throws Exception {
        MvcResult result = call(POST, "/api/products", adminToken(), """
                {"name":"%s","type":"PLATE","price":%s,"stock":%d}""".formatted(name, price, stock));
        return lng(result, "$.id");
    }

    protected int stockOf(long productId) throws Exception {
        return integer(call(GET, "/api/admin/products/" + productId, adminToken(), null), "$.stock");
    }

    /** Places a one-line order as the given customer and returns the order id. */
    protected long createOrder(Customer customer, long productId, int quantity) throws Exception {
        MvcResult result = call(POST, "/api/orders", customer.token(), orderJson(productId, quantity));
        return lng(result, "$.id");
    }

    protected MvcResult setOrderStatus(long orderId, String newStatus) throws Exception {
        return call(PATCH, "/api/admin/orders/" + orderId + "/status", adminToken(),
                "{\"status\":\"%s\"}".formatted(newStatus));
    }
}
