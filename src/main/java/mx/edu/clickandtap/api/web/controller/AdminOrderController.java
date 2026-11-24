package mx.edu.clickandtap.api.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.OrderStatus;
import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.service.OrderService;
import mx.edu.clickandtap.api.web.dto.ApiDto.AdminOrderResponse;
import mx.edu.clickandtap.api.web.dto.ApiDto.OrderStatusRequest;
import mx.edu.clickandtap.api.web.dto.ApiDto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {
    private final OrderService orderService;

    /** All orders, optionally filtered by status, e.g. ?status=PAID&sort=createdAt,asc */
    @GetMapping
    public PageResponse<AdminOrderResponse> all(@RequestParam(required = false) OrderStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String sort) {
        PageQuery query = PageQuery.of(page, size, sort, OrderService.SORT_FIELDS, OrderService.DEFAULT_SORT);
        return PageResponse.from(orderService.getAll(status, query), AdminOrderResponse::from);
    }

    @PatchMapping("/{id}/status")
    public AdminOrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest r) {
        return AdminOrderResponse.from(orderService.updateStatus(id, r.status()));
    }
}
