package mx.edu.clickandtap.api.persistence.crud;

import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductCrudRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByActiveTrue();
    List<ProductEntity> findByTypeAndActiveTrue(ProductType type);
}