package mx.edu.clickandtap.api.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.service.OrderService;
import mx.edu.clickandtap.api.web.dto.ApiDto.OrderRequest;
import mx.edu.clickandtap.api.web.dto.ApiDto.OrderResponse;
import mx.edu.clickandtap.api.web.dto.ApiDto.PageResponse;
import mx.edu.clickandtap.api.web.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody OrderRequest r) {
        return OrderResponse.from(orderService.create(user.id(), r.shippingAddress(), r.toItems()));
    }

    /** My orders, newest first by default. sort: id, createdAt, total or status, e.g. sort=total,desc */
    @GetMapping
    public PageResponse<OrderResponse> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String sort) {
        PageQuery query = PageQuery.of(page, size, sort, OrderService.SORT_FIELDS, OrderService.DEFAULT_SORT);
        return PageResponse.from(orderService.getByUser(user.id(), query), OrderResponse::from);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return OrderResponse.from(orderService.getById(id, user.id(), user.isAdmin()));
    }
}
