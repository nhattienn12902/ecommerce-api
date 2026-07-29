package com.nhattienn.ecommerce.order;

import java.util.Set;

public enum OrderStatus {
    PENDING,
    PAID,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Set<OrderStatus> NO_TRANSITION = Set.of();

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<OrderStatus> allowedTargets() {
        return switch (this) {
            case PENDING   -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED   -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED, PAID -> NO_TRANSITION;
        };
    }
}