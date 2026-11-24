package mx.edu.clickandtap.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductIntegrationTest extends IntegrationTestBase {

    @Test
    void adminCreatesAProductAndSeesItsExactStock() throws Exception {
        MvcResult result = call(POST, "/api/products", adminToken(), """
                {"name":"%s","description":"Placa","type":"PLATE","price":249.00,"stock":7}""".formatted(unique("placa")));

        assertThat(status(result)).isEqualTo(201);
        assertThat(integer(result, "$.stock")).isEqualTo(7);
        assertThat(bool(result, "$.active")).isTrue();
    }

    @Test
    void thePublicViewShowsAvailabilityButNotTheExactInventory() throws Exception {
        long inStock = createProduct(unique("con-stock"), "10.00", 3);
        long soldOut = createProduct(unique("agotado"), "10.00", 0);

        MvcResult available = call(GET, "/api/products/" + inStock, null, null);
        MvcResult unavailable = call(GET, "/api/products/" + soldOut, null, null);

        assertThat(status(available)).isEqualTo(200);
        assertThat(bool(available, "$.inStock")).isTrue();
        assertThat(bool(unavailable, "$.inStock")).isFalse();
        assertThat(body(available)).doesNotContain("\"stock\"").doesNotContain("\"active\"");
    }

    @Test
    void invalidProductDataIsRejectedWithAClearMessage() throws Exception {
        MvcResult negativePrice = call(POST, "/api/products", adminToken(),
                """
                {"name":"x","type":"PLATE","price":-1,"stock":1}""");
        MvcResult longName = call(POST, "/api/products", adminToken(),
                """
                {"name":"%s","type":"PLATE","price":1,"stock":1}""".formatted("x".repeat(200)));
        MvcResult badType = call(POST, "/api/products", adminToken(),
                """
                {"name":"x","type":"SPACESHIP","price":1,"stock":1}""");
        MvcResult badImage = call(POST, "/api/products", adminToken(),
                """
                {"name":"x","type":"PLATE","price":1,"stock":1,"imageUrl":"javascript:alert(1)"}""");

        assertThat(status(negativePrice)).isEqualTo(400);
        assertThat(str(negativePrice, "$.message")).contains("price");
        assertThat(status(longName)).isEqualTo(400);
        assertThat(str(longName, "$.message")).contains("name");
        assertThat(status(badType)).isEqualTo(400);
        assertThat(status(badImage)).isEqualTo(400);
    }

    @Test
    void theCatalogIsPaginated() throws Exception {
        createProduct(unique("pag-a"), "10.00", 1);
        createProduct(unique("pag-b"), "20.00", 1);
        createProduct(unique("pag-c"), "30.00", 1);

        MvcResult page = call(GET, "/api/products?page=0&size=1", null, null);

        assertThat(status(page)).isEqualTo(200);
        assertThat(integer(page, "$.size")).isEqualTo(1);
        assertThat(integer(page, "$.page")).isZero();
        List<Object> items = at(page, "$.items");
        assertThat(items).hasSize(1);
        assertThat(lng(page, "$.totalItems")).isGreaterThanOrEqualTo(3);
        assertThat(integer(page, "$.totalPages")).isEqualTo((int) lng(page, "$.totalItems"));
    }

    @Test
    void theCatalogCanBeSortedByPrice() throws Exception {
        createProduct(unique("ord-a"), "11.00", 1);
        createProduct(unique("ord-b"), "99.00", 1);

        List<Number> descending = at(call(GET, "/api/products?size=100&sort=price,desc", null, null), "$.items[*].price");
        List<Number> ascending = at(call(GET, "/api/products?size=100&sort=price,asc", null, null), "$.items[*].price");

        assertThat(descending).isSortedAccordingTo((a, b) -> Double.compare(b.doubleValue(), a.doubleValue()));
        assertThat(ascending).isSortedAccordingTo((a, b) -> Double.compare(a.doubleValue(), b.doubleValue()));
    }

    @Test
    void invalidPaginationParametersAre400() throws Exception {
        assertThat(status(call(GET, "/api/products?sort=passwordHash", null, null))).isEqualTo(400);
        assertThat(status(call(GET, "/api/products?sort=price,sideways", null, null))).isEqualTo(400);
        assertThat(status(call(GET, "/api/products?size=101", null, null))).isEqualTo(400);
        assertThat(status(call(GET, "/api/products?size=0", null, null))).isEqualTo(400);
        assertThat(status(call(GET, "/api/products?page=-1", null, null))).isEqualTo(400);
    }

    @Test
    void anUnknownProductIs404() throws Exception {
        assertThat(status(call(GET, "/api/products/999999999", null, null))).isEqualTo(404);
    }

    @Test
    void deletingAProductDeactivatesItInsteadOfRemovingIt() throws Exception {
        String name = unique("borrable");
        long id = createProduct(name, "10.00", 5);

        assertThat(status(call(DELETE, "/api/products/" + id, adminToken(), null))).isEqualTo(204);
        assertThat(status(call(DELETE, "/api/products/" + id, adminToken(), null))).as("idempotent").isEqualTo(204);

        // Gone for the public...
        assertThat(status(call(GET, "/api/products/" + id, null, null))).isEqualTo(404);
        assertThat(body(call(GET, "/api/products?size=100&sort=id,desc", null, null))).doesNotContain(name);
        // ...but the admin still sees it, flagged as inactive.
        MvcResult asAdmin = call(GET, "/api/admin/products/" + id, adminToken(), null);
        assertThat(status(asAdmin)).isEqualTo(200);
        assertThat(bool(asAdmin, "$.active")).isFalse();
    }

    @Test
    void anInactiveProductCanBeReactivatedWithAnExplicitPut() throws Exception {
        String name = unique("reactivable");
        long id = createProduct(name, "10.00", 5);
        call(DELETE, "/api/products/" + id, adminToken(), null);

        MvcResult reactivated = call(PUT, "/api/products/" + id, adminToken(), putJson(name, true));

        assertThat(status(reactivated)).isEqualTo(200);
        assertThat(status(call(GET, "/api/products/" + id, null, null))).isEqualTo(200);
    }

    @Test
    void putMustSayWhetherTheProductIsActiveSoItCannotReactivateByAccident() throws Exception {
        long id = createProduct(unique("sin-active"), "10.00", 5);
        call(DELETE, "/api/products/" + id, adminToken(), null);

        MvcResult result = call(PUT, "/api/products/" + id, adminToken(), """
                {"name":"x","type":"PLATE","price":10,"customizable":true}""");

        assertThat(status(result)).isEqualTo(400);
        assertThat(str(result, "$.message")).contains("active");
        assertThat(status(call(GET, "/api/products/" + id, null, null))).as("still inactive").isEqualTo(404);
    }

    @Test
    void putChangesProductDataButNeverTheStock() throws Exception {
        long id = createProduct(unique("editable"), "10.00", 7);

        MvcResult result = call(PUT, "/api/products/" + id, adminToken(), """
                {"name":"Nuevo nombre","type":"KEYCHAIN","price":55.50,"customizable":false,"active":true,"stock":9999}""");

        assertThat(status(result)).isEqualTo(200);
        assertThat(str(result, "$.name")).isEqualTo("Nuevo nombre");
        assertThat(str(result, "$.type")).isEqualTo("KEYCHAIN");
        assertThat(dbl(result, "$.price")).isEqualTo(55.50);
        assertThat(stockOf(id)).isEqualTo(7);
    }

    @Test
    void stockIsAdjustedWithADeltaAndNeverGoesNegative() throws Exception {
        long id = createProduct(unique("stock"), "10.00", 10);

        MvcResult add = call(PATCH, "/api/products/" + id + "/stock", adminToken(), "{\"delta\":5}");
        MvcResult remove = call(PATCH, "/api/products/" + id + "/stock", adminToken(), "{\"delta\":-15}");
        MvcResult tooMuch = call(PATCH, "/api/products/" + id + "/stock", adminToken(), "{\"delta\":-1}");
        MvcResult zero = call(PATCH, "/api/products/" + id + "/stock", adminToken(), "{\"delta\":0}");

        assertThat(integer(add, "$.stock")).isEqualTo(15);
        assertThat(integer(remove, "$.stock")).isZero();
        assertThat(status(tooMuch)).isEqualTo(400);
        assertThat(str(tooMuch, "$.message")).isEqualTo("Stock cannot go below zero");
        assertThat(status(zero)).isEqualTo(400);
        assertThat(stockOf(id)).isZero();
    }

    private static String putJson(String name, boolean active) {
        return """
                {"name":"%s","type":"PLATE","price":10.00,"customizable":true,"active":%b}""".formatted(name, active);
    }
}
