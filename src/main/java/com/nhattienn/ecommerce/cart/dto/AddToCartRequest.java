package com.nhattienn.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddToCartRequest(
        @NotNull(message = "Product ID must not be null.")
        Long productId,

        @Min(value = 1, message = "Quantity must be at least 1.")
        int quantity
) {}