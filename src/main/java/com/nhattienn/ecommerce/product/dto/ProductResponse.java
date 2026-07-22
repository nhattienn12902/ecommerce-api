package com.nhattienn.ecommerce.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Long categoryId,
        String categoryName,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}