package mx.edu.clickandtap.api.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;

import static mx.edu.clickandtap.api.domain.OrderStatus.CANCELLED;
import static mx.edu.clickandtap.api.domain.OrderStatus.DELIVERED;
import static mx.edu.clickandtap.api.domain.OrderStatus.IN_PRODUCTION;
import static mx.edu.clickandtap.api.domain.OrderStatus.PAID;
import static mx.edu.clickandtap.api.domain.OrderStatus.PENDING;
import static mx.edu.clickandtap.api.domain.OrderStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {
    /** The lifecycle written out by hand, so a change to the enum has to be a conscious one. */
    private static final Map<OrderStatus, Set<OrderStatus>> EXPECTED = Map.of(
            PENDING, Set.of(PAID, CANCELLED),
            PAID, Set.of(IN_PRODUCTION, CANCELLED),
            IN_PRODUCTION, Set.of(SHIPPED),
            SHIPPED, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of());

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void everyTransitionMatchesTheLifecycle(OrderStatus from) {
        for (OrderStatus to : OrderStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(EXPECTED.get(from).contains(to));
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED"})
    void finalStatesHaveNoWayOut(OrderStatus finalStatus) {
        assertThat(finalStatus.allowedNext()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"IN_PRODUCTION", "SHIPPED", "DELIVERED", "CANCELLED"})
    void orderCannotBeCancelledOnceProductionStarted(OrderStatus from) {
        assertThat(from.canTransitionTo(CANCELLED)).isFalse();
    }
}
