package mx.edu.clickandtap.api.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Order;
import mx.edu.clickandtap.api.domain.service.OrderService;
import mx.edu.clickandtap.api.web.dto.ApiDto.OrderRequest;
import mx.edu.clickandtap.api.web.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody OrderRequest r) {
        return orderService.create(user.id(), r.shippingAddress(), r.toItems());
    }

    @GetMapping
    public List<Order> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.getByUser(user.id());
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.getById(id, user.id(), user.isAdmin());
    }
}