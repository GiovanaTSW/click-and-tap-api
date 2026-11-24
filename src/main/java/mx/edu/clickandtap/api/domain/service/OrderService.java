package mx.edu.clickandtap.api.domain.service;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.domain.OrderItem;
import mx.edu.clickandtap.api.domain.OrderStatus;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.ConflictException;
import mx.edu.clickandtap.api.domain.exception.ForbiddenException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.pagination.PageResult;
import mx.edu.clickandtap.api.domain.repository.OrderRepository;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class OrderService {
    /** Fields clients may sort by, and the order used when they send none. */
    public static final Set<String> SORT_FIELDS = Set.of("id", "createdAt", "total", "status");
    public static final String DEFAULT_SORT = "createdAt,desc";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /** El precio siempre se toma de la BD, nunca del cliente. */
    @Transactional
    public Order create(Long userId, String shippingAddress, List<OrderItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BadRequestException("Order must have at least one item");
        }
        BigDecimal total = BigDecimal.ZERO;
        Map<Long, Product> products = new HashMap<>();
        // Sorted by product id: every order locks stock rows in the same order, so two orders
        // containing the same products can never deadlock each other.
        Map<Long, Integer> quantityByProduct = new TreeMap<>();

        for (OrderItem item : requestedItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Quantity must be greater than zero");
            }
            Product product = products.get(item.getProductId());
            if (product == null) {
                product = productRepository.getById(item.getProductId())
                        .orElseThrow(() -> new NotFoundException("Product not found: " + item.getProductId()));
                products.put(product.getId(), product);
            }
            if (!product.isActive()) {
                throw new BadRequestException("Product is not available: " + product.getName());
            }
            item.setId(null);
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            quantityByProduct.merge(product.getId(), item.getQuantity(), Integer::sum);
        }

        // Atomic check-and-decrement. If any product lacks stock the exception rolls back the
        // whole transaction, including the decrements already done for other products.
        quantityByProduct.forEach((productId, quantity) -> {
            if (!productRepository.reserveStock(productId, quantity)) {
                throw new BadRequestException("Not enough stock for: " + products.get(productId).getName());
            }
        });

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setTotal(total);
        order.setShippingAddress(shippingAddress);
        order.setItems(requestedItems);
        return orderRepository.save(order);
    }

    public PageResult<Order> getByUser(Long userId, PageQuery query) {
        return orderRepository.getByUserId(userId, query);
    }

    /** Admin list, optionally filtered by status (null = all). */
    public PageResult<Order> getAll(OrderStatus status, PageQuery query) {
        return orderRepository.getAll(status, query);
    }

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
        OrderStatus current = order.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            throw new BadRequestException("Cannot change order status from " + current + " to " + newStatus);
        }
        // Compare-and-set: if two admins act at the same time only one wins, so stock is
        // restored at most once per order.
        if (!orderRepository.transitionStatus(orderId, current, newStatus)) {
            throw new ConflictException("The order status was changed by someone else. Reload and try again");
        }
        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                productRepository.adjustStock(item.getProductId(), item.getQuantity());
            }
        }
        return orderRepository.getById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }
}
