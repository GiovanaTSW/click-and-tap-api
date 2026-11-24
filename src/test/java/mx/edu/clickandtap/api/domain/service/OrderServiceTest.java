package mx.edu.clickandtap.api.domain.service;

import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.domain.OrderItem;
import mx.edu.clickandtap.api.domain.OrderStatus;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.ConflictException;
import mx.edu.clickandtap.api.domain.exception.ForbiddenException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.repository.OrderRepository;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @InjectMocks OrderService service;

    // ---------- create ----------

    @Test
    void createUsesDatabasePricesAndIgnoresTheClientPrice() {
        when(productRepository.getById(1L)).thenReturn(Optional.of(product(1, "Placa NFC", "249.00", true)));
        when(productRepository.getById(2L)).thenReturn(Optional.of(product(2, "Llavero", "99.50", true)));
        when(productRepository.reserveStock(anyLong(), anyInt())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderItem first = item(1, 2);
        first.setUnitPrice(new BigDecimal("0.01")); // a malicious client tries to pay one cent
        Order order = service.create(7L, "Calle 1", List.of(first, item(2, 1)));

        assertThat(order.getTotal()).isEqualByComparingTo("597.50"); // 2 x 249.00 + 99.50
        assertThat(order.getUserId()).isEqualTo(7L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getShippingAddress()).isEqualTo("Calle 1");
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("249.00");
        assertThat(order.getItems().get(1).getUnitPrice()).isEqualByComparingTo("99.50");
    }

    @Test
    void createSnapshotsTheProductNameOnEachItem() {
        when(productRepository.getById(1L)).thenReturn(Optional.of(product(1, "Placa NFC", "249.00", true)));
        when(productRepository.reserveStock(1L, 1)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = service.create(7L, "Calle 1", List.of(item(1, 1)));

        assertThat(order.getItems().get(0).getProductName()).isEqualTo("Placa NFC");
    }

    @Test
    void createReservesStockOncePerProductInProductIdOrder() {
        when(productRepository.getById(1L)).thenReturn(Optional.of(product(1, "A", "10.00", true)));
        when(productRepository.getById(3L)).thenReturn(Optional.of(product(3, "C", "20.00", true)));
        when(productRepository.reserveStock(anyLong(), anyInt())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Product 3 is listed first and twice; product 1 once.
        service.create(7L, "Calle 1", List.of(item(3, 1), item(1, 2), item(3, 4)));

        InOrder inOrder = inOrder(productRepository);
        inOrder.verify(productRepository).reserveStock(1L, 2);
        inOrder.verify(productRepository).reserveStock(3L, 5); // 1 + 4 added together
        verify(productRepository, times(1)).getById(3L);       // loaded once even if repeated
    }

    @Test
    void createFailsWithoutSavingWhenStockIsNotEnough() {
        when(productRepository.getById(1L)).thenReturn(Optional.of(product(1, "Placa NFC", "249.00", true)));
        when(productRepository.reserveStock(1L, 2)).thenReturn(false);

        assertThatThrownBy(() -> service.create(7L, "Calle 1", List.of(item(1, 2))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Not enough stock for: Placa NFC");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createRejectsInactiveProductsBeforeTouchingStock() {
        when(productRepository.getById(1L)).thenReturn(Optional.of(product(1, "Viejo", "10.00", false)));

        assertThatThrownBy(() -> service.create(7L, "Calle 1", List.of(item(1, 1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available");
        verify(productRepository, never()).reserveStock(anyLong(), anyInt());
    }

    @Test
    void createRejectsUnknownProducts() {
        when(productRepository.getById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(7L, "Calle 1", List.of(item(99, 1))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createRejectsEmptyOrMissingItems() {
        assertThatThrownBy(() -> service.create(7L, "Calle 1", List.of())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(7L, "Calle 1", null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createRejectsZeroOrNegativeQuantities() {
        assertThatThrownBy(() -> service.create(7L, "Calle 1", List.of(item(1, 0))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Quantity");
        assertThatThrownBy(() -> service.create(7L, "Calle 1", List.of(item(1, -3))))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(productRepository);
    }

    // ---------- updateStatus ----------

    @Test
    void cancellingAPaidOrderGivesTheStockBack() {
        Order order = order(5, OrderStatus.PAID, item(1, 2), item(2, 3));
        when(orderRepository.getById(5L)).thenReturn(Optional.of(order));
        when(orderRepository.transitionStatus(5L, OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);

        service.updateStatus(5L, OrderStatus.CANCELLED);

        verify(productRepository).adjustStock(1L, 2);
        verify(productRepository).adjustStock(2L, 3);
    }

    @Test
    void movingForwardNeverTouchesStock() {
        Order order = order(5, OrderStatus.PENDING, item(1, 2));
        when(orderRepository.getById(5L)).thenReturn(Optional.of(order));
        when(orderRepository.transitionStatus(5L, OrderStatus.PENDING, OrderStatus.PAID)).thenReturn(true);

        service.updateStatus(5L, OrderStatus.PAID);

        verifyNoInteractions(productRepository);
    }

    @Test
    void invalidTransitionIsRejectedWithoutChangingAnything() {
        when(orderRepository.getById(5L)).thenReturn(Optional.of(order(5, OrderStatus.DELIVERED, item(1, 1))));

        assertThatThrownBy(() -> service.updateStatus(5L, OrderStatus.PENDING))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot change order status from DELIVERED to PENDING");
        verify(orderRepository, never()).transitionStatus(anyLong(), any(), any());
        verifyNoInteractions(productRepository);
    }

    @Test
    void aCancelledOrderCannotBeCancelledAgain() {
        when(orderRepository.getById(5L)).thenReturn(Optional.of(order(5, OrderStatus.CANCELLED, item(1, 1))));

        assertThatThrownBy(() -> service.updateStatus(5L, OrderStatus.CANCELLED))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(productRepository); // so stock cannot be restored twice
    }

    @Test
    void losingTheRaceToAnotherAdminGivesConflictAndDoesNotRestoreStock() {
        Order order = order(5, OrderStatus.PAID, item(1, 2));
        when(orderRepository.getById(5L)).thenReturn(Optional.of(order));
        when(orderRepository.transitionStatus(5L, OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(false);

        assertThatThrownBy(() -> service.updateStatus(5L, OrderStatus.CANCELLED))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(productRepository);
    }

    @Test
    void updateStatusOfUnknownOrderIsNotFound() {
        when(orderRepository.getById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(404L, OrderStatus.PAID)).isInstanceOf(NotFoundException.class);
    }

    // ---------- getById ----------

    @Test
    void ownerCanReadTheirOrder() {
        Order order = order(5, OrderStatus.PENDING);
        order.setUserId(7L);
        when(orderRepository.getById(5L)).thenReturn(Optional.of(order));

        assertThat(service.getById(5L, 7L, false)).isSameAs(order);
    }

    @Test
    void anotherCustomerCannotReadTheOrder() {
        Order order = order(5, OrderStatus.PENDING);
        order.setUserId(7L);
        when(orderRepository.getById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getById(5L, 8L, false)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCanReadAnyOrder() {
        Order order = order(5, OrderStatus.PENDING);
        order.setUserId(7L);
        when(orderRepository.getById(5L)).thenReturn(Optional.of(order));

        assertThat(service.getById(5L, 99L, true)).isSameAs(order);
    }

    // ---------- helpers ----------

    private Product product(long id, String name, String price, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setStock(10);
        p.setActive(active);
        return p;
    }

    private OrderItem item(long productId, int quantity) {
        OrderItem i = new OrderItem();
        i.setProductId(productId);
        i.setQuantity(quantity);
        return i;
    }

    private Order order(long id, OrderStatus status, OrderItem... items) {
        Order o = new Order();
        o.setId(id);
        o.setStatus(status);
        o.setItems(List.of(items));
        return o;
    }
}
