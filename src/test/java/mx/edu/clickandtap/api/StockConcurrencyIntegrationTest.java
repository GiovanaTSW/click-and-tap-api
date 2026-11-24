package mx.edu.clickandtap.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tests that mocks can never replace: many real requests hitting a real PostgreSQL at the same
 * moment. They replace the manual Test-ConcurrentOrders script.
 */
class StockConcurrencyIntegrationTest extends IntegrationTestBase {
    private static final int THREADS = 20;

    @Test
    void twentySimultaneousOrdersForFiveUnitsSellExactlyFive() throws Exception {
        long productId = createProduct(unique("ultimas-5"), "10.00", 5);
        Customer customer = registerCustomer();

        List<Integer> statuses = runAtTheSameTime(THREADS, i ->
                () -> status(call(POST, "/api/orders", customer.token(), orderJson(productId, 1))));

        assertThat(statuses).filteredOn(s -> s == 201).as("orders accepted").hasSize(5);
        assertThat(statuses).filteredOn(s -> s == 400).as("orders refused for lack of stock").hasSize(THREADS - 5);
        assertThat(stockOf(productId)).as("stock never negative, nothing lost").isZero();
        assertThat(lng(call(GET, "/api/orders", customer.token(), null), "$.totalItems")).isEqualTo(5);
    }

    @Test
    void ordersWithTheSameProductsInOppositeOrderNeverDeadlock() throws Exception {
        long a = createProduct(unique("deadlock-a"), "10.00", 1000);
        long b = createProduct(unique("deadlock-b"), "10.00", 1000);
        Customer customer = registerCustomer();
        String aThenB = twoLineOrder(a, b);
        String bThenA = twoLineOrder(b, a);

        List<Integer> statuses = runAtTheSameTime(THREADS, i ->
                () -> status(call(POST, "/api/orders", customer.token(), i % 2 == 0 ? aThenB : bThenA)));

        assertThat(statuses).as("no 500 from a deadlock").containsOnly(201);
        assertThat(stockOf(a)).isEqualTo(1000 - THREADS);
        assertThat(stockOf(b)).isEqualTo(1000 - THREADS);
    }

    @Test
    void tenAdminsCancellingTheSameOrderRestoreTheStockOnlyOnce() throws Exception {
        long productId = createProduct(unique("cancel-race"), "10.00", 10);
        Customer customer = registerCustomer();
        long orderId = createOrder(customer, productId, 4);
        assertThat(stockOf(productId)).isEqualTo(6);

        List<Integer> statuses = runAtTheSameTime(10, i -> () -> status(setOrderStatus(orderId, "CANCELLED")));

        assertThat(statuses).filteredOn(s -> s == 200).as("exactly one winner").hasSize(1);
        assertThat(statuses).filteredOn(s -> s != 200).as("the others are told no").allMatch(s -> s == 400 || s == 409);
        assertThat(stockOf(productId)).as("4 units returned once, not 40").isEqualTo(10);
    }

    @Test
    void stockAdjustmentsAndOrdersAtTheSameTimeLoseNothing() throws Exception {
        long productId = createProduct(unique("mixto"), "10.00", 100);
        Customer customer = registerCustomer();

        // 10 customers take 1 unit each while 10 admin calls add 2 units each: 100 - 10 + 20 = 110
        List<Integer> statuses = runAtTheSameTime(20, i -> i % 2 == 0
                ? () -> status(call(POST, "/api/orders", customer.token(), orderJson(productId, 1)))
                : () -> status(call(PATCH, "/api/products/" + productId + "/stock", adminToken(), "{\"delta\":2}")));

        assertThat(statuses).filteredOn(s -> s >= 500).isEmpty();
        assertThat(stockOf(productId)).isEqualTo(110);
    }

    // ---------- helpers ----------

    private static String twoLineOrder(long first, long second) {
        return """
                {"shippingAddress":"Calle 1","items":[
                  {"productId":%d,"quantity":1},{"productId":%d,"quantity":1}]}""".formatted(first, second);
    }

    /**
     * Starts {@code count} tasks and releases them together, so the requests really overlap.
     * {@code taskFactory} receives the task's index and returns what it should run.
     */
    private List<Integer> runAtTheSameTime(int count, java.util.function.IntFunction<Callable<Integer>> taskFactory)
            throws Exception {
        adminToken(); // log in first, so every thread starts with the token already cached
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Integer> task = taskFactory.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) results.add(future.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
