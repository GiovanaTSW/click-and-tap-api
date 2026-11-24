package mx.edu.clickandtap.api.domain;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class Order {
    private Long id;
    private Long userId;
    private OrderStatus status;
    private BigDecimal total;
    private String shippingAddress;
    private Instant createdAt;
    private List<OrderItem> items;
}
