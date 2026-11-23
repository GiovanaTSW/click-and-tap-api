package mx.edu.clickandtap.api.persistence;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import mx.edu.clickandtap.api.persistence.crud.ProductCrudRepository;
import mx.edu.clickandtap.api.persistence.mapper.ProductMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductCrudRepository crud;
    private final ProductMapper mapper;

    @Override
    public List<Product> getAllActive() { return mapper.toProducts(crud.findByActiveTrue()); }

    @Override
    public List<Product> getByType(ProductType type) { return mapper.toProducts(crud.findByTypeAndActiveTrue(type)); }

    @Override
    public Optional<Product> getById(Long id) { return crud.findById(id).map(mapper::toProduct); }

    @Override
    public Product save(Product product) { return mapper.toProduct(crud.save(mapper.toEntity(product))); }

    @Override
    public void delete(Long id) { crud.deleteById(id); }
}