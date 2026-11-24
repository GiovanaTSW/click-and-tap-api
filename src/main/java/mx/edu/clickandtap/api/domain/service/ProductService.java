package mx.edu.clickandtap.api.domain.service;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.pagination.PageResult;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    /** Fields clients may sort by, and the order used when they send none. */
    public static final Set<String> SORT_FIELDS = Set.of("id", "name", "price", "type");
    public static final String DEFAULT_SORT = "name,asc";

    /** Public catalog: active products only, optionally filtered by type. */
    public PageResult<Product> getCatalog(ProductType type, PageQuery query) {
        return productRepository.getActive(type, query);
    }

    /** Admin list: includes inactive products so they can be reviewed and reactivated. */
    public PageResult<Product> getAllForAdmin(PageQuery query) {
        return productRepository.getAll(query);
    }

    /** Public detail: an inactive (soft-deleted) product looks like it does not exist. */
    public Product getPublicById(Long id) {
        Product product = getById(id);
        if (!product.isActive()) throw new NotFoundException("Product not found");
        return product;
    }

    /** Admin access: also returns inactive products so they can be edited or reactivated. */
    public Product getById(Long id) {
        return productRepository.getById(id).orElseThrow(() -> new NotFoundException("Product not found"));
    }

    @Transactional
    public Product create(Product product) {
        validate(product);
        if (product.getStock() == null || product.getStock() < 0) {
            throw new BadRequestException("Stock must be zero or greater");
        }
        product.setId(null);
        product.setActive(true);
        return productRepository.save(product);
    }

    /** Edits product data. Stock is NOT touched here: use adjustStock, which is atomic. */
    @Transactional
    public Product update(Long id, Product changes) {
        validate(changes);
        Product existing = getById(id);
        existing.setName(changes.getName());
        existing.setDescription(changes.getDescription());
        existing.setType(changes.getType());
        existing.setPrice(changes.getPrice());
        existing.setImageUrl(changes.getImageUrl());
        existing.setCustomizable(changes.isCustomizable());
        existing.setActive(changes.isActive());
        return productRepository.save(existing);
    }

    /**
     * Soft delete: the product disappears from the catalog but old orders keep pointing to it.
     * Calling it twice is harmless.
     */
    @Transactional
    public void deactivate(Long id) {
        Product existing = getById(id);
        if (existing.isActive()) {
            existing.setActive(false);
            productRepository.save(existing);
        }
    }

    /** Adds (or removes, if negative) units of stock in a single atomic statement. */
    @Transactional
    public Product adjustStock(Long id, int delta) {
        getById(id);
        if (delta == 0) throw new BadRequestException("Delta must not be zero");
        if (!productRepository.adjustStock(id, delta)) {
            throw new BadRequestException("Stock cannot go below zero");
        }
        return getById(id);
    }

    private void validate(Product p) {
        if (p.getName() == null || p.getName().isBlank()) throw new BadRequestException("Name is required");
        if (p.getType() == null) throw new BadRequestException("Type is required");
        if (p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Price must be zero or greater");
        }
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
