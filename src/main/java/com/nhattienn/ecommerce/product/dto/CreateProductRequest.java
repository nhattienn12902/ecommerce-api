package com.nhattienn.ecommerce.product.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest(

        @NotBlank(message = "Product name must not be blank.")
        @Size(max = 255, message = "Product name must not exceed 255 characters.")
        String name,

        String description,

        @NotNull(message = "Price must not be null.")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be non-negative.")
        @Digits(integer = 15, fraction = 4, message = "Price must have at most 15 integer digits and 4 decimal places.")
        BigDecimal price,

        @NotNull(message = "Category ID must not be null.")
        Long categoryId
) {}