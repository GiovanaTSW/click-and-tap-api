package mx.edu.clickandtap.api.persistence;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.domain.repository.OrderRepository;
import mx.edu.clickandtap.api.persistence.crud.OrderCrudRepository;
import mx.edu.clickandtap.api.persistence.mapper.OrderMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderCrudRepository crud;
    private final OrderMapper mapper;

    @Override
    public List<Order> getAll() { return mapper.toOrders(crud.findAll()); }

    @Override
    public List<Order> getByUserId(Long userId) { return mapper.toOrders(crud.findByUserIdOrderByCreatedAtDesc(userId)); }

    @Override
    public Optional<Order> getById(Long id) { return crud.findById(id).map(mapper::toOrder); }

    @Override
    public Order save(Order order) { return mapper.toOrder(crud.save(mapper.toEntity(order))); }
}