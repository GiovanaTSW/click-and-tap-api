package mx.edu.clickandtap.api.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.edu.clickandtap.api.domain.ProductType;
import org.hibernate.annotations.DynamicUpdate;
import java.math.BigDecimal;

/**
 * Stock is changed by atomic UPDATE statements (see ProductCrudRepository). @DynamicUpdate makes
 * Hibernate write only the columns that actually changed, so editing a product's name or price
 * can never overwrite a stock value that an order changed in the meantime.
 */
@Getter @Setter
@DynamicUpdate
@Entity
@Table(name = "products")
public class ProductEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    @Enumerated(EnumType.STRING)
    private ProductType type;
    private BigDecimal price;
    private Integer stock;
    @Column(name = "image_url")
    private String imageUrl;
    private boolean customizable;
    private boolean active;
}