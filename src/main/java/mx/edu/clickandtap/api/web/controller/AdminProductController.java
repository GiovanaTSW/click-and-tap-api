package mx.edu.clickandtap.api.web.controller;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.service.ProductService;
import mx.edu.clickandtap.api.web.dto.ApiDto.AdminProductResponse;
import mx.edu.clickandtap.api.web.dto.ApiDto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (see SecurityConfig: /api/admin/**). Unlike the public catalog it also shows inactive products. */
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {
    private final ProductService productService;

    @GetMapping
    public PageResponse<AdminProductResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String sort) {
        PageQuery query = PageQuery.of(page, size, sort, ProductService.SORT_FIELDS, ProductService.DEFAULT_SORT);
        return PageResponse.from(productService.getAllForAdmin(query), AdminProductResponse::from);
    }

    @GetMapping("/{id}")
    public AdminProductResponse get(@PathVariable Long id) {
        return AdminProductResponse.from(productService.getById(id));
    }
}
