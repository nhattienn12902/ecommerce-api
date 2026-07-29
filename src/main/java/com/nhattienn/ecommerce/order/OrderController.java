package com.nhattienn.ecommerce.order;

import com.nhattienn.ecommerce.common.response.ApiResponse;
import com.nhattienn.ecommerce.order.dto.CheckoutRequest;
import com.nhattienn.ecommerce.order.dto.OrderResponse;
import com.nhattienn.ecommerce.order.dto.UpdateOrderStatusRequest;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderQueryService orderQueryService;
    private final OrderService orderService;

    public OrderController(CheckoutService checkoutService,
                          OrderQueryService orderQueryService,
                        OrderService orderService) {
        this.checkoutService = checkoutService;
        this.orderQueryService = orderQueryService;
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            Authentication authentication,
            @Valid @RequestBody CheckoutRequest request) {
        UUID userId = currentUserId(authentication);
        OrderResponse response = checkoutService.checkout(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> findMyOrders(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        UUID userId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(orderQueryService.findByUserId(userId, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> findById(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID userId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(orderQueryService.findByIdForUser(id, userId)));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateStatus(id, request.status())));
    }
}