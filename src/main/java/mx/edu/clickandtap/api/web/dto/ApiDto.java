package mx.edu.clickandtap.api.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
import mx.edu.clickandtap.api.domain.User;

import java.math.BigDecimal;
import java.util.List;

public final class ApiDto {
    private ApiDto() {}

    // ---------- Errores ----------
    public record ErrorResponse(int status, String message) {}

    // ---------- Auth ----------
    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            String phone) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserResponse(Long id, String name, String email, String phone, Role role) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole());
        }
    }

    public record AuthResponse(String token, UserResponse user) {}

    // ---------- Productos ----------
    public record ProductRequest(
            @NotBlank String name,
            String description,
            @NotNull ProductType type,
            @NotNull @PositiveOrZero BigDecimal price,
            @NotNull @PositiveOrZero Integer stock,
            String imageUrl,
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

    // ---------- Pedidos ----------
    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Positive Integer quantity,
            String customizationNotes) {}

    public record OrderRequest(
            @NotBlank String shippingAddress,
            @NotEmpty @Valid List<OrderItemRequest> items) {
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
            @NotBlank String label,
            @NotBlank String url,
            String icon,
            Boolean active) {}

    public record ExperienceRequest(
            @NotNull ExperienceType type,
            @NotBlank String title,
            String slug,
            String description,
            Boolean active,
            @Valid List<LinkRequest> links) {
        public DigitalExperience toDomain() {
            DigitalExperience e = new DigitalExperience();
            e.setType(type);
            e.setTitle(title);
            e.setSlug(slug);
            e.setDescription(description);
            e.setActive(active == null || active);
            if (links != null) {
                e.setLinks(links.stream().map(l -> {
                    ExperienceLink link = new ExperienceLink();
                    link.setLabel(l.label());
                    link.setUrl(l.url());
                    link.setIcon(l.icon());
                    link.setActive(l.active() == null || l.active());
                    return link;
                }).toList());
            }
            return e;
        }
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