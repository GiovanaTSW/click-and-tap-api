package mx.edu.clickandtap.api.persistence.mapper;

import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.persistence.entity.OrderEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring", uses = OrderItemMapper.class)
public interface OrderMapper {
    Order toOrder(OrderEntity entity);
    List<Order> toOrders(List<OrderEntity> entities);
    OrderEntity toEntity(Order order);

    @AfterMapping
    default void linkItems(@MappingTarget OrderEntity entity) {
        if (entity.getItems() != null) entity.getItems().forEach(i -> i.setOrder(entity));
    }
}