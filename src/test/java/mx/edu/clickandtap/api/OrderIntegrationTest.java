package mx.edu.clickandtap.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIntegrationTest extends IntegrationTestBase {

    // ---------- creating orders ----------

    @Test
    void anOrderIsPricedByTheServerAndTakesStock() throws Exception {
        String name = unique("placa");
        long productId = createProduct(name, "249.00", 10);
        Customer customer = registerCustomer();

        MvcResult order = call(POST, "/api/orders", customer.token(), orderJson(productId, 2));

        assertThat(status(order)).isEqualTo(201);
        assertThat(str(order, "$.status")).isEqualTo("PENDING");
        assertThat(dbl(order, "$.total")).isEqualTo(498.0);
        assertThat(str(order, "$.items[0].productName")).isEqualTo(name);
        assertThat(dbl(order, "$.items[0].unitPrice")).isEqualTo(249.0);
        assertThat(integer(order, "$.items[0].quantity")).isEqualTo(2);
        assertThat(stockOf(productId)).isEqualTo(8);
        assertThat(body(order)).as("customers do not see internal user ids").doesNotContain("userId");
    }

    @Test
    void theClientCannotChoosePriceOrTotal() throws Exception {
        long productId = createProduct(unique("caro"), "100.00", 10);
        Customer customer = registerCustomer();

        MvcResult order = call(POST, "/api/orders", customer.token(), """
                {"shippingAddress":"Calle 1","total":0.01,
                 "items":[{"productId":%d,"quantity":1,"unitPrice":0.01}]}""".formatted(productId));

        assertThat(status(order)).isEqualTo(201);
        assertThat(dbl(order, "$.total")).isEqualTo(100.0);
        assertThat(dbl(order, "$.items[0].unitPrice")).isEqualTo(100.0);
    }

    @Test
    void orderingMoreThanTheStockFailsAndChangesNothing() throws Exception {
        long productId = createProduct(unique("escaso"), "10.00", 2);
        Customer customer = registerCustomer();

        MvcResult order = call(POST, "/api/orders", customer.token(), orderJson(productId, 3));

        assertThat(status(order)).isEqualTo(400);
        assertThat(str(order, "$.message")).startsWith("Not enough stock");
        assertThat(stockOf(productId)).isEqualTo(2);
        assertThat(lng(call(GET, "/api/orders", customer.token(), null), "$.totalItems")).isZero();
    }

    @Test
    void ifOneLineFailsTheStockTakenByTheOtherLinesIsGivenBack() throws Exception {
        long plenty = createProduct(unique("sobra"), "10.00", 5);
        long scarce = createProduct(unique("falta"), "10.00", 1);
        Customer customer = registerCustomer();

        MvcResult order = call(POST, "/api/orders", customer.token(), """
                {"shippingAddress":"Calle 1","items":[
                  {"productId":%d,"quantity":2},{"productId":%d,"quantity":2}]}""".formatted(plenty, scarce));

        assertThat(status(order)).isEqualTo(400);
        assertThat(stockOf(plenty)).as("rolled back").isEqualTo(5);
        assertThat(stockOf(scarce)).isEqualTo(1);
    }

    @Test
    void anInactiveProductCannotBeOrdered() throws Exception {
        long productId = createProduct(unique("retirado"), "10.00", 5);
        call(DELETE, "/api/products/" + productId, adminToken(), null);
        Customer customer = registerCustomer();

        MvcResult order = call(POST, "/api/orders", customer.token(), orderJson(productId, 1));

        assertThat(status(order)).isEqualTo(400);
        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    void invalidOrdersAreRejected() throws Exception {
        long productId = createProduct(unique("valido"), "10.00", 5);
        Customer customer = registerCustomer();

        assertThat(status(call(POST, "/api/orders", customer.token(),
                "{\"shippingAddress\":\"Calle 1\",\"items\":[]}"))).as("no items").isEqualTo(400);
        assertThat(status(call(POST, "/api/orders", customer.token(),
                orderJson(productId, 0)))).as("zero quantity").isEqualTo(400);
        assertThat(status(call(POST, "/api/orders", customer.token(),
                orderJson(productId, 101)))).as("absurd quantity").isEqualTo(400);
        assertThat(status(call(POST, "/api/orders", customer.token(),
                "{\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(productId)))).as("no address").isEqualTo(400);
        assertThat(status(call(POST, "/api/orders", customer.token(),
                orderJson(999999999L, 1)))).as("unknown product").isEqualTo(404);
        assertThat(stockOf(productId)).isEqualTo(5);
    }

    // ---------- reading orders ----------

    @Test
    void onlyTheOwnerOrAnAdminCanReadAnOrder() throws Exception {
        long productId = createProduct(unique("privado"), "10.00", 5);
        Customer owner = registerCustomer();
        Customer stranger = registerCustomer();
        long orderId = createOrder(owner, productId, 1);

        assertThat(status(call(GET, "/api/orders/" + orderId, owner.token(), null))).isEqualTo(200);
        assertThat(status(call(GET, "/api/orders/" + orderId, adminToken(), null))).isEqualTo(200);
        assertThat(status(call(GET, "/api/orders/" + orderId, stranger.token(), null))).isEqualTo(403);
        assertThat(status(call(GET, "/api/orders/999999999", owner.token(), null))).isEqualTo(404);
    }

    @Test
    void myOrdersListsOnlyMineNewestFirstAndIsPaginated() throws Exception {
        long productId = createProduct(unique("lista"), "10.00", 10);
        Customer me = registerCustomer();
        Customer other = registerCustomer();
        long first = createOrder(me, productId, 1);
        long second = createOrder(me, productId, 1);
        createOrder(other, productId, 1);

        MvcResult all = call(GET, "/api/orders", me.token(), null);
        MvcResult onePerPage = call(GET, "/api/orders?size=1", me.token(), null);
        MvcResult oldestFirst = call(GET, "/api/orders?sort=createdAt,asc", me.token(), null);

        assertThat(lng(all, "$.totalItems")).isEqualTo(2);
        assertThat(lng(all, "$.items[0].id")).as("newest first by default").isEqualTo(second);
        assertThat(lng(oldestFirst, "$.items[0].id")).isEqualTo(first);
        assertThat(integer(onePerPage, "$.totalPages")).isEqualTo(2);
        List<Object> items = at(onePerPage, "$.items");
        assertThat(items).hasSize(1);
        assertThat(status(call(GET, "/api/orders?sort=userId", me.token(), null))).isEqualTo(400);
    }

    @Test
    void adminListsOrdersByStatusAndSeesWhoBoughtThem() throws Exception {
        long productId = createProduct(unique("admin-lista"), "10.00", 10);
        Customer customer = registerCustomer();
        long pending = createOrder(customer, productId, 1);
        long paid = createOrder(customer, productId, 1);
        setOrderStatus(paid, "PAID");

        MvcResult pendingList = call(GET, "/api/admin/orders?status=PENDING&size=100", adminToken(), null);
        MvcResult paidList = call(GET, "/api/admin/orders?status=PAID&size=100", adminToken(), null);

        List<Number> pendingIds = at(pendingList, "$.items[*].id");
        List<Number> paidIds = at(paidList, "$.items[*].id");
        assertThat(pendingIds).extracting(Number::longValue).contains(pending).doesNotContain(paid);
        assertThat(paidIds).extracting(Number::longValue).contains(paid).doesNotContain(pending);
        assertThat(body(pendingList)).contains("\"userId\":" + customer.id());
        assertThat(status(call(GET, "/api/admin/orders?status=SPACESHIP", adminToken(), null))).isEqualTo(400);
    }

    @Test
    void anOldOrderStillShowsWhatWasBoughtAfterTheProductIsRenamedAndDeleted() throws Exception {
        String originalName = unique("original");
        long productId = createProduct(originalName, "10.00", 5);
        Customer customer = registerCustomer();
        long orderId = createOrder(customer, productId, 1);

        call(PUT, "/api/products/" + productId, adminToken(), """
                {"name":"Nombre nuevo","type":"PLATE","price":99.00,"customizable":true,"active":true}""");
        call(DELETE, "/api/products/" + productId, adminToken(), null);

        MvcResult order = call(GET, "/api/orders/" + orderId, customer.token(), null);
        assertThat(status(order)).isEqualTo(200);
        assertThat(str(order, "$.items[0].productName")).isEqualTo(originalName);
        assertThat(dbl(order, "$.items[0].unitPrice")).as("price paid is kept").isEqualTo(10.0);
        assertThat(dbl(order, "$.total")).isEqualTo(10.0);
    }

    // ---------- status lifecycle ----------

    @Test
    void anOrderFollowsTheLifecycleAndCannotSkipOrGoBack() throws Exception {
        long productId = createProduct(unique("flujo"), "10.00", 5);
        Customer customer = registerCustomer();
        long orderId = createOrder(customer, productId, 1);

        assertThat(status(setOrderStatus(orderId, "DELIVERED"))).as("cannot skip ahead").isEqualTo(400);
        assertThat(status(setOrderStatus(orderId, "PAID"))).isEqualTo(200);
        assertThat(status(setOrderStatus(orderId, "PENDING"))).as("cannot go back").isEqualTo(400);
        assertThat(status(setOrderStatus(orderId, "IN_PRODUCTION"))).isEqualTo(200);
        assertThat(status(setOrderStatus(orderId, "CANCELLED"))).as("too late to cancel").isEqualTo(400);
        assertThat(status(setOrderStatus(orderId, "SHIPPED"))).isEqualTo(200);
        MvcResult delivered = setOrderStatus(orderId, "DELIVERED");
        assertThat(status(delivered)).isEqualTo(200);
        assertThat(str(delivered, "$.status")).isEqualTo("DELIVERED");
        assertThat(status(setOrderStatus(orderId, "PENDING"))).as("final state").isEqualTo(400);
        assertThat(stockOf(productId)).as("stock untouched along the way").isEqualTo(4);
    }

    @Test
    void cancellingGivesTheStockBackExactlyOnce() throws Exception {
        long productId = createProduct(unique("cancelable"), "10.00", 10);
        Customer customer = registerCustomer();
        long orderId = createOrder(customer, productId, 3);
        assertThat(stockOf(productId)).isEqualTo(7);

        MvcResult cancelled = setOrderStatus(orderId, "CANCELLED");
        MvcResult again = setOrderStatus(orderId, "CANCELLED");
        MvcResult reopen = setOrderStatus(orderId, "PENDING");

        assertThat(status(cancelled)).isEqualTo(200);
        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(status(again)).isEqualTo(400);
        assertThat(status(reopen)).isEqualTo(400);
        assertThat(stockOf(productId)).as("not restored a second time").isEqualTo(10);
    }

    @Test
    void customersCannotChangeOrderStatus() throws Exception {
        long productId = createProduct(unique("sin-permiso"), "10.00", 5);
        Customer customer = registerCustomer();
        long orderId = createOrder(customer, productId, 1);

        MvcResult result = call(PATCH, "/api/admin/orders/" + orderId + "/status", customer.token(),
                "{\"status\":\"PAID\"}");

        assertThat(status(result)).isEqualTo(403);
        assertThat(str(call(GET, "/api/orders/" + orderId, customer.token(), null), "$.status")).isEqualTo("PENDING");
    }

    @Test
    void settingAnUnknownStatusIs400() throws Exception {
        assertThat(status(setOrderStatus(1L, "SPACESHIP"))).isEqualTo(400);
        assertThat(status(call(PATCH, "/api/admin/orders/1/status", adminToken(), "{}"))).isEqualTo(400);
    }
}
