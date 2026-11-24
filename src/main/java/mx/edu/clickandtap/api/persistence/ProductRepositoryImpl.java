package mx.edu.clickandtap.api.persistence;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.pagination.PageResult;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import mx.edu.clickandtap.api.persistence.crud.ProductCrudRepository;
import mx.edu.clickandtap.api.persistence.entity.ProductEntity;
import mx.edu.clickandtap.api.persistence.mapper.ProductMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/**
 * With spring.jpa.open-in-view=false the Hibernate session closes when the transaction ends, so every
 * method that maps entities to domain objects runs inside its own transaction (or joins the service's).
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductCrudRepository crud;
    private final ProductMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> getActive(ProductType type, PageQuery query) {
        Pageable pageable = PageSupport.toPageable(query);
        Page<ProductEntity> page = type == null
                ? crud.findByActiveTrue(pageable)
                : crud.findByTypeAndActiveTrue(type, pageable);
        return PageSupport.toResult(page, mapper::toProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> getAll(PageQuery query) {
        return PageSupport.toResult(crud.findAll(PageSupport.toPageable(query)), mapper::toProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() { return crud.count(); }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getById(Long id) { return crud.findById(id).map(mapper::toProduct); }

    @Override
    @Transactional
    public Product save(Product product) { return mapper.toProduct(crud.save(mapper.toEntity(product))); }

    @Override
    @Transactional
    public boolean reserveStock(Long id, int quantity) { return crud.reserveStock(id, quantity) == 1; }

    @Override
    @Transactional
    public boolean adjustStock(Long id, int delta) { return crud.adjustStock(id, delta) == 1; }
}
