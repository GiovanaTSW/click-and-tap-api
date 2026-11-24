package mx.edu.clickandtap.api.domain.repository;

import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.pagination.PageResult;
import java.util.Optional;

public interface ProductRepository {
    /** Active products only, optionally filtered by type (null = all types). */
    PageResult<Product> getActive(ProductType type, PageQuery query);

    /** Every product, including inactive ones (admin). */
    PageResult<Product> getAll(PageQuery query);

    long count();
    Optional<Product> getById(Long id);
    Product save(Product product);

    /**
     * Atomically takes {@code quantity} units of stock from an active product.
     * Returns false (and changes nothing) if the product is inactive or has less stock.
     */
    boolean reserveStock(Long id, int quantity);

    /**
     * Atomically adds {@code delta} (positive or negative) to the stock.
     * Returns false (and changes nothing) if the result would be negative.
     */
    boolean adjustStock(Long id, int delta);
}
