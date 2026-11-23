package mx.edu.clickandtap.api.domain.repository;

import mx.edu.clickandtap.api.domain.Order;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    List<Order> getAll();
    List<Order> getByUserId(Long userId);
    Optional<Order> getById(Long id);
    Order save(Order order);
}
