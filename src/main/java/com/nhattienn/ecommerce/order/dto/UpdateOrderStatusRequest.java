package com.nhattienn.ecommerce.order.dto;

import com.nhattienn.ecommerce.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Status must not be null.")
        OrderStatus status
) {}