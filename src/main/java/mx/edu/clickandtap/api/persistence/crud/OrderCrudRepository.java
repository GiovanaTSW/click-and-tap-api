package mx.edu.clickandtap.api.persistence.crud;

import mx.edu.clickandtap.api.domain.OrderStatus;
import mx.edu.clickandtap.api.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderCrudRepository extends JpaRepository<OrderEntity, Long> {
    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OrderEntity o set o.status = :next where o.id = :id and o.status = :expected")
    int transitionStatus(@Param("id") Long id,
                         @Param("expected") OrderStatus expected,
                         @Param("next") OrderStatus next);
}
