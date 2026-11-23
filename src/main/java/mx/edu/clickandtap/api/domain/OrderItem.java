package mx.edu.clickandtap.api.domain;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class OrderItem {
    private Long id;
    private Long orderId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String customizationNotes;
}
