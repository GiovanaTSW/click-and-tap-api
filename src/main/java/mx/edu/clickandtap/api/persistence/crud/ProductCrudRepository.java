package mx.edu.clickandtap.api.persistence.crud;

import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCrudRepository extends JpaRepository<ProductEntity, Long> {
    Page<ProductEntity> findByActiveTrue(Pageable pageable);
    Page<ProductEntity> findByTypeAndActiveTrue(ProductType type, Pageable pageable);

    /** One SQL statement: the check and the decrement cannot be interleaved with another order. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductEntity p set p.stock = p.stock - :quantity "
            + "where p.id = :id and p.active = true and p.stock >= :quantity")
    int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductEntity p set p.stock = p.stock + :delta "
            + "where p.id = :id and p.stock + :delta >= 0")
    int adjustStock(@Param("id") Long id, @Param("delta") int delta);
}
