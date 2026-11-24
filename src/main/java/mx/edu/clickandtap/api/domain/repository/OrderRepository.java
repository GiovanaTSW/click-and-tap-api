package mx.edu.clickandtap.api.domain.repository;

import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.domain.OrderStatus;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.pagination.PageResult;
import java.util.Optional;

public interface OrderRepository {
    /** Every order, optionally filtered by status (null = all statuses). */
    PageResult<Order> getAll(OrderStatus status, PageQuery query);

    PageResult<Order> getByUserId(Long userId, PageQuery query);
    Optional<Order> getById(Long id);
    Order save(Order order);

    /**
     * Compare-and-set: changes the status only if it is still {@code expected}.
     * Returns false if someone else changed it first, so only one caller wins a race.
     */
    boolean transitionStatus(Long id, OrderStatus expected, OrderStatus next);
}
