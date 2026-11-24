package mx.edu.clickandtap.api.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.service.ProductService;
import mx.edu.clickandtap.api.web.dto.ApiDto.AdminProductResponse;
import mx.edu.clickandtap.api.web.dto.ApiDto.PageResponse;
import mx.edu.clickandtap.api.web.dto.ApiDto.ProductRequest;
import mx.edu.clickandtap.api.web.dto.ApiDto.ProductResponse;
import mx.edu.clickandtap.api.web.dto.ApiDto.ProductUpdateRequest;
import mx.edu.clickandtap.api.web.dto.ApiDto.StockAdjustRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    /** Public catalog. sort: id, name, price or type, e.g. sort=price,desc */
    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(required = false) ProductType type,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String sort) {
        PageQuery query = PageQuery.of(page, size, sort, ProductService.SORT_FIELDS, ProductService.DEFAULT_SORT);
        return PageResponse.from(productService.getCatalog(type, query), ProductResponse::from);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(productService.getPublicById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse create(@Valid @RequestBody ProductRequest r) {
        return AdminProductResponse.from(productService.create(r.toDomain()));
    }

    @PutMapping("/{id}")
    public AdminProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest r) {
        return AdminProductResponse.from(productService.update(id, r.toDomain()));
    }

    @PatchMapping("/{id}/stock")
    public AdminProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustRequest r) {
        return AdminProductResponse.from(productService.adjustStock(id, r.delta()));
    }

    /** Soft delete: the product is deactivated, not removed. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { productService.deactivate(id); }
}
