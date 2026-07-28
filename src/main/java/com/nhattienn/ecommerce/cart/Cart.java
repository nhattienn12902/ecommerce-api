package com.nhattienn.ecommerce.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    private UUID userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    private Instant updatedAt;

    public void addOrUpdateItem(CartItem newItem) {
        items.stream()
                .filter(i -> i.getProductId().equals(newItem.getProductId()))
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.setQuantity(existing.getQuantity() + newItem.getQuantity()),
                        () -> items.add(newItem)
                );
        this.updatedAt = Instant.now();
    }

    public void updateItemQuantity(Long productId, int quantity) {
        items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .ifPresent(item -> item.setQuantity(quantity));
        this.updatedAt = Instant.now();
    }

    public void removeItem(Long productId) {
        items.removeIf(i -> i.getProductId().equals(productId));
        this.updatedAt = Instant.now();
    }
    @JsonIgnore
    public boolean isEmpty() {
        return items.isEmpty();
    }

    
}