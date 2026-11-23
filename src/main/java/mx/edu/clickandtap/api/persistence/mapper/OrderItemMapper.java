package mx.edu.clickandtap.api.persistence.mapper;

import mx.edu.clickandtap.api.domain.OrderItem;
import mx.edu.clickandtap.api.persistence.entity.OrderItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {
    OrderItem toItem(OrderItemEntity entity);

    @Mapping(target = "order", ignore = true)
    OrderItemEntity toEntity(OrderItem item);
}