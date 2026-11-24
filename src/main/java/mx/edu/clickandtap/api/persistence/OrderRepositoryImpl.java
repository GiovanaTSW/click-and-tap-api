package mx.edu.clickandtap.api.persistence;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.domain.OrderStatus;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.pagination.PageResult;
import mx.edu.clickandtap.api.domain.repository.OrderRepository;
import mx.edu.clickandtap.api.persistence.crud.OrderCrudRepository;
import mx.edu.clickandtap.api.persistence.entity.OrderEntity;
import mx.edu.clickandtap.api.persistence.mapper.OrderMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/**
 * Order items are a lazy collection. Mapping to domain objects happens inside these transactions;
 * hibernate.default_batch_fetch_size loads the items of a whole page in a few queries instead of one per order.
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderCrudRepository crud;
    private final OrderMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> getAll(OrderStatus status, PageQuery query) {
        Pageable pageable = PageSupport.toPageable(query);
        Page<OrderEntity> page = status == null ? crud.findAll(pageable) : crud.findByStatus(status, pageable);
        return PageSupport.toResult(page, mapper::toOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> getByUserId(Long userId, PageQuery query) {
        return PageSupport.toResult(crud.findByUserId(userId, PageSupport.toPageable(query)), mapper::toOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> getById(Long id) { return crud.findById(id).map(mapper::toOrder); }

    @Override
    @Transactional
    public Order save(Order order) { return mapper.toOrder(crud.save(mapper.toEntity(order))); }

    @Override
    @Transactional
    public boolean transitionStatus(Long id, OrderStatus expected, OrderStatus next) {
        return crud.transitionStatus(id, expected, next) == 1;
    }
}
