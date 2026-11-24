package mx.edu.clickandtap.api.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import mx.edu.clickandtap.api.domain.DigitalExperience;
import mx.edu.clickandtap.api.domain.ExperienceLink;
import mx.edu.clickandtap.api.domain.ExperienceType;
import mx.edu.clickandtap.api.domain.OrderItem;
import mx.edu.clickandtap.api.domain.OrderStatus;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.Role;
import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.domain.User;
import mx.edu.clickandtap.api.domain.pagination.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Size limits mirror the database columns (see db/migration), so oversized input is rejected
 * with a clear 400 instead of reaching PostgreSQL.
 */
public final class ApiDto {
    private ApiDto() {}

    // ---------- Errores ----------
    public record ErrorResponse(int status, String message) {}

    // ---------- Auth ----------
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 160) String email,
            // BCrypt only uses the first 72 bytes of a password.
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 30) String phone) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 72) String password) {}

    public record UserResponse(Long id, String name, String email, String phone, Role role) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole());
        }
    }

    public record AuthResponse(String token, UserResponse user) {}

    // ---------- Productos ----------
    public record ProductRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description,
            @NotNull ProductType type,
            @NotNull @PositiveOrZero BigDecimal price,
            @NotNull @PositiveOrZero Integer stock,
            @Size(max = 500) String imageUrl,
            Boolean customizable,
            Boolean active) {
        public Product toDomain() {
            Product p = new Product();
            p.setName(name);
            p.setDescription(description);
            p.setType(type);
            p.setPrice(price);
            p.setStock(stock);
            p.setImageUrl(imageUrl);
            p.setCustomizable(customizable == null || customizable);
            p.setActive(active == null || active);
            return p;
        }
    }

    /** PUT /api/products/{id}: full replacement of product data, without stock (see StockAdjustRequest). */
    public record ProductUpdateRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description,
            @NotNull ProductType type,
            @NotNull @PositiveOrZero BigDecimal price,
            @Size(max = 500) String imageUrl,
            @NotNull Boolean customizable,
            @NotNull Boolean active) {
        public Product toDomain() {
            Product p = new Product();
            p.setName(name);
            p.setDescription(description);
            p.setType(type);
            p.setPrice(price);
            p.setImageUrl(imageUrl);
            p.setCustomizable(customizable);
            p.setActive(active);
            return p;
        }
    }

    /** PATCH /api/products/{id}/stock: positive adds units, negative removes them. */
    public record StockAdjustRequest(@NotNull @Min(-100000) @Max(100000) Integer delta) {}

    // ---------- Pedidos ----------
    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Positive @Max(100) Integer quantity,
            @Size(max = 500) String customizationNotes) {}

    public record OrderRequest(
            @NotBlank @Size(max = 500) String shippingAddress,
            @NotEmpty @Size(max = 50) @Valid List<OrderItemRequest> items) {
        public List<OrderItem> toItems() {
            return items.stream().map(i -> {
                OrderItem item = new OrderItem();
                item.setProductId(i.productId());
                item.setQuantity(i.quantity());
                item.setCustomizationNotes(i.customizationNotes());
                return item;
            }).toList();
        }
    }

    public record OrderStatusRequest(@NotNull OrderStatus status) {}

    // ---------- Experiencias digitales ----------
    public record LinkRequest(
            @NotBlank @Size(max = 100) String label,
            @NotBlank @Size(max = 500) String url,
            @Size(max = 50) String icon,
            Boolean active) {}

    public record ExperienceRequest(
            @NotNull ExperienceType type,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 100) String slug,
            @Size(max = 2000) String description,
            Boolean active,
            @Size(max = 20) @Valid List<LinkRequest> links) {
        public DigitalExperience toDomain() {
            DigitalExperience e = new DigitalExperience();
            e.setType(type);
            e.setTitle(title);
            e.setSlug(slug);
            e.setDescription(description);
            e.setActive(active == null || active);
            if (links != null) e.setLinks(toLinks(links));
            return e;
        }
    }

    /** PUT /api/experiences/{id}: type and slug cannot change, and "active" must be explicit. */
    public record ExperienceUpdateRequest(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 2000) String description,
            @NotNull Boolean active,
            @Size(max = 20) @Valid List<LinkRequest> links) {
        public DigitalExperience toDomain() {
            DigitalExperience e = new DigitalExperience();
            e.setTitle(title);
            e.setDescription(description);
            e.setActive(active);
            if (links != null) e.setLinks(toLinks(links));
            return e;
        }
    }

    private static List<ExperienceLink> toLinks(List<LinkRequest> requests) {
        return requests.stream().map(l -> {
            ExperienceLink link = new ExperienceLink();
            link.setLabel(l.label());
            link.setUrl(l.url());
            link.setIcon(l.icon());
            link.setActive(l.active() == null || l.active());
            return link;
        }).toList();
    }

    // ---------- Respuestas (nunca se devuelven objetos de dominio) ----------

    /** A page of results. "page" starts at 0. */
    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        public static <D, R> PageResponse<R> from(PageResult<D> result, Function<D, R> mapper) {
            return new PageResponse<>(result.items().stream().map(mapper).toList(),
                    result.page(), result.size(), result.totalItems(), result.totalPages());
        }
    }

    /** Public catalog view: shows availability, not the exact inventory. */
    public record ProductResponse(
            Long id, String name, String description, ProductType type, BigDecimal price,
            String imageUrl, boolean customizable, boolean inStock) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getType(), p.getPrice(),
                    p.getImageUrl(), p.isCustomizable(), p.getStock() != null && p.getStock() > 0);
        }
    }

    /** Admin view of a product: adds exact stock and the active flag. */
    public record AdminProductResponse(
            Long id, String name, String description, ProductType type, BigDecimal price, Integer stock,
            String imageUrl, boolean customizable, boolean active) {
        public static AdminProductResponse from(Product p) {
            return new AdminProductResponse(p.getId(), p.getName(), p.getDescription(), p.getType(), p.getPrice(),
                    p.getStock(), p.getImageUrl(), p.isCustomizable(), p.isActive());
        }
    }

    public record OrderItemResponse(
            Long id, Long productId, String productName, Integer quantity, BigDecimal unitPrice,
            String customizationNotes) {
        public static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(i.getId(), i.getProductId(), i.getProductName(), i.getQuantity(),
                    i.getUnitPrice(), i.getCustomizationNotes());
        }
    }

    public record OrderResponse(
            Long id, OrderStatus status, BigDecimal total, String shippingAddress, Instant createdAt,
            List<OrderItemResponse> items) {
        public static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getStatus(), o.getTotal(), o.getShippingAddress(),
                    o.getCreatedAt(), itemsOf(o));
        }
    }

    /** Admin view of an order: adds the customer's user id. */
    public record AdminOrderResponse(
            Long id, Long userId, OrderStatus status, BigDecimal total, String shippingAddress,
            Instant createdAt, List<OrderItemResponse> items) {
        public static AdminOrderResponse from(Order o) {
            return new AdminOrderResponse(o.getId(), o.getUserId(), o.getStatus(), o.getTotal(),
                    o.getShippingAddress(), o.getCreatedAt(), itemsOf(o));
        }
    }

    public record ExperienceLinkResponse(
            Long id, String label, String url, String icon, Integer position, boolean active) {
        public static ExperienceLinkResponse from(ExperienceLink l) {
            return new ExperienceLinkResponse(l.getId(), l.getLabel(), l.getUrl(), l.getIcon(),
                    l.getPosition(), l.isActive());
        }
    }

    public record ExperienceResponse(
            Long id, Long orderItemId, ExperienceType type, String title, String slug, String description,
            boolean active, Instant createdAt, List<ExperienceLinkResponse> links) {
        public static ExperienceResponse from(DigitalExperience e) {
            List<ExperienceLinkResponse> links = e.getLinks() == null ? List.of()
                    : e.getLinks().stream().map(ExperienceLinkResponse::from).toList();
            return new ExperienceResponse(e.getId(), e.getOrderItemId(), e.getType(), e.getTitle(), e.getSlug(),
                    e.getDescription(), e.isActive(), e.getCreatedAt(), links);
        }
    }

    private static List<OrderItemResponse> itemsOf(Order o) {
        return o.getItems() == null ? List.of() : o.getItems().stream().map(OrderItemResponse::from).toList();
    }

    /** Lo que ve quien escanea el NFC/QR: sin ids ni datos del dueño. */
    public record PublicLink(String label, String url, String icon) {}

    public record PublicExperienceResponse(
            String slug, ExperienceType type, String title, String description, List<PublicLink> links) {
        public static PublicExperienceResponse from(DigitalExperience e) {
            List<PublicLink> links = e.getLinks() == null ? List.of()
                    : e.getLinks().stream().map(l -> new PublicLink(l.getLabel(), l.getUrl(), l.getIcon())).toList();
            return new PublicExperienceResponse(e.getSlug(), e.getType(), e.getTitle(), e.getDescription(), links);
        }
    }
}
