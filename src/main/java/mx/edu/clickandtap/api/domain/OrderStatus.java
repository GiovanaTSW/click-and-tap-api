package mx.edu.clickandtap.api.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle:
 * PENDING -> PAID -> IN_PRODUCTION -> SHIPPED -> DELIVERED
 * An order can be cancelled only before production starts (PENDING or PAID).
 * DELIVERED and CANCELLED are final.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    IN_PRODUCTION,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(IN_PRODUCTION, CANCELLED);
            case IN_PRODUCTION -> EnumSet.of(SHIPPED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }
}
