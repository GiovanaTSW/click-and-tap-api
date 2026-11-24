package mx.edu.clickandtap.api.domain.service;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    public List<Product> getAll() { return productRepository.getAllActive(); }

    public List<Product> getByType(ProductType type) { return productRepository.getByType(type); }

    public Product getById(Long id) {
        return productRepository.getById(id).orElseThrow(() -> new NotFoundException("Product not found"));
    }

    public Product create(Product product) {
        validate(product);
        product.setId(null);
        product.setActive(true);
        return productRepository.save(product);
    }

    public Product update(Long id, Product changes) {
        validate(changes);
        Product existing = getById(id);
        existing.setName(changes.getName());
        existing.setDescription(changes.getDescription());
        existing.setType(changes.getType());
        existing.setPrice(changes.getPrice());
        existing.setStock(changes.getStock());
        existing.setImageUrl(changes.getImageUrl());
        existing.setCustomizable(changes.isCustomizable());
        existing.setActive(changes.isActive());
        return productRepository.save(existing);
    }

    public void delete(Long id) {
        getById(id);
        productRepository.delete(id);
    }

    private void validate(Product p) {
        if (p.getName() == null || p.getName().isBlank()) throw new BadRequestException("Name is required");
        if (p.getType() == null) throw new BadRequestException("Type is required");
        if (p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Price must be zero or greater");
        }
        if (p.getStock() == null || p.getStock() < 0) throw new BadRequestException("Stock must be zero or greater");
        validateImageUrl(p);
    }

    /** Only web URLs are accepted: javascript:, data: and similar schemes could run code in a client. */
    private void validateImageUrl(Product p) {
        String url = p.getImageUrl();
        if (url == null || url.isBlank()) {
            p.setImageUrl(null);
            return;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            throw new BadRequestException("Image url must start with http:// or https://");
        }
        p.setImageUrl(trimmed);
    }
}