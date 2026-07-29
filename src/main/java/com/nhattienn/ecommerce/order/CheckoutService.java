package com.nhattienn.ecommerce.order;

import com.nhattienn.ecommerce.cart.Cart;
import com.nhattienn.ecommerce.cart.CartRepository;
import com.nhattienn.ecommerce.common.exception.BusinessException;
import com.nhattienn.ecommerce.order.dto.CheckoutRequest;
import com.nhattienn.ecommerce.order.dto.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class CheckoutService {

    private final CartRepository cartRepository;
    private final OrderService orderService;

    public CheckoutService(CartRepository cartRepository, OrderService orderService) {
        this.cartRepository = cartRepository;
        this.orderService = orderService;
    }

    public OrderResponse checkout(UUID userId, CheckoutRequest request) {
        // ① Đọc Cart từ Redis — ngoài transaction
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("EMPTY_CART", "Cart is empty."));

        if (cart.isEmpty()) {
            throw new BusinessException("EMPTY_CART", "Cart is empty.");
        }

        // ②③ Trừ stock + tạo Order — trong transaction (qua proxy của OrderService)
        Order order = orderService.createOrder(userId, cart, request.shippingAddress());

        // ④ Xóa Cart khỏi Redis — sau khi transaction commit thành công
        cartRepository.deleteByUserId(userId);
        log.info("Order {} created for user {}, cart cleared.", order.getId(), userId);

        // ⑤ Trả về response
        return OrderResponse.from(order);
    }
}