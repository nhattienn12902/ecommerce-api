package com.nhattienn.ecommerce.cart;

import com.nhattienn.ecommerce.cart.dto.AddToCartRequest;
import com.nhattienn.ecommerce.cart.dto.CartResponse;
import com.nhattienn.ecommerce.cart.dto.UpdateCartItemRequest;
import com.nhattienn.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(cartService.getCart(userId)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            Authentication authentication,
            @Valid @RequestBody AddToCartRequest request) {
        UUID userId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(cartService.addItem(userId, request)));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        UUID userId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(cartService.updateItem(userId, productId, request)));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            Authentication authentication,
            @PathVariable Long productId) {
        UUID userId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(cartService.removeItem(userId, productId)));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}