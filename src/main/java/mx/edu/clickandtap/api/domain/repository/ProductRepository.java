package mx.edu.clickandtap.api.domain.repository;

import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> getAllActive();
    List<Product> getByType(ProductType type);
    Optional<Product> getById(Long id);
    Product save(Product product);
    void delete(Long id);
}
