package mx.edu.clickandtap.api.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.edu.clickandtap.api.domain.ProductType;
import java.math.BigDecimal;

@Getter @Setter
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