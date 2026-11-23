package mx.edu.clickandtap.api.domain.service;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.domain.OrderItem;
import mx.edu.clickandtap.api.domain.OrderStatus;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.ForbiddenException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.repository.OrderRepository;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /** El precio siempre se toma de la BD, nunca del cliente. */
    @Transactional
    public Order create(Long userId, String shippingAddress, List<OrderItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BadRequestException("Order must have at least one item");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : requestedItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Quantity must be greater than zero");
            }
            Product product = productRepository.getById(item.getProductId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + item.getProductId()));
            if (!product.isActive()) {
                throw new BadRequestException("Product is not available: " + product.getName());
            }
            if (product.getStock() < item.getQuantity()) {
                throw new BadRequestException("Not enough stock for: " + product.getName());
            }
            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);

            item.setId(null);
            item.setUnitPrice(product.getPrice());
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setTotal(total);
        order.setShippingAddress(shippingAddress);
        order.setItems(requestedItems);
        return orderRepository.save(order);
    }

    public List<Order> getByUser(Long userId) { return orderRepository.getByUserId(userId); }

    public List<Order> getAll() { return orderRepository.getAll(); }

    public Order getById(Long orderId, Long requesterId, boolean isAdmin) {
        Order order = orderRepository.getById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (!isAdmin && !order.getUserId().equals(requesterId)) {
            throw new ForbiddenException("You cannot access this order");
        }
        return order;
    }

    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.getById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (newStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                productRepository.getById(item.getProductId()).ifPresent(p -> {
                    p.setStock(p.getStock() + item.getQuantity());
                    productRepository.save(p);
                });
            }
        }
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }
}