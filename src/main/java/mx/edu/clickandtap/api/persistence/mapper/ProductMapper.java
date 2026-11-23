package mx.edu.clickandtap.api.persistence.mapper;

import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.persistence.entity.ProductEntity;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    Product toProduct(ProductEntity entity);
    List<Product> toProducts(List<ProductEntity> entities);
    ProductEntity toEntity(Product product);
}