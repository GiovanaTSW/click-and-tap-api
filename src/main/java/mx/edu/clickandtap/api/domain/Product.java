package mx.edu.clickandtap.api.domain;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class Product {
    private Long id;
    private String name;
    private String description;
    private ProductType type;
    private BigDecimal price;
    private Integer stock;
    private String imageUrl;
    private boolean customizable;
    private boolean active;
}
