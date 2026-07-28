package com.nhattienn.ecommerce.cart.dto;

import com.nhattienn.ecommerce.cart.Cart;
import com.nhattienn.ecommerce.cart.CartItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID userId,
        List<CartItemResponse> items,
        BigDecimal totalPrice,
        int totalItems,
        Instant updatedAt
) {
    public record CartItemResponse(
            Long productId,
            String productName,
            BigDecimal price,
            int quantity,
            BigDecimal subtotal
    ) {}

    public static CartResponse from(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> new CartItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))))
                .toList();

        BigDecimal totalPrice = itemResponses.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        return new CartResponse(
                cart.getUserId(),
                itemResponses,
                totalPrice,
                totalItems,
                cart.getUpdatedAt());
    }
}