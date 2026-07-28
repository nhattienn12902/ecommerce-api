package com.nhattienn.ecommerce.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    private Long productId;
    private String productName;
    private BigDecimal price;       // snapshot tại thời điểm thêm vào giỏ
    private int quantity;
}