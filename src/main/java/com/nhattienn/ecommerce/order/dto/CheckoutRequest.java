package com.nhattienn.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank(message = "Shipping address must not be blank.")
        @Size(max = 500, message = "Shipping address must not exceed 500 characters.")
        String shippingAddress
) {}