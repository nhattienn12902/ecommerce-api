package com.nhattienn.ecommerce.order;

import com.nhattienn.ecommerce.cart.Cart;
import com.nhattienn.ecommerce.cart.CartItem;
import com.nhattienn.ecommerce.common.exception.BusinessException;
import com.nhattienn.ecommerce.common.exception.InsufficientStockException;
import com.nhattienn.ecommerce.common.exception.ResourceNotFoundException;
import com.nhattienn.ecommerce.order.dto.OrderResponse;
import com.nhattienn.ecommerce.product.Inventory;
import com.nhattienn.ecommerce.product.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderService {

    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;

    public OrderService(InventoryRepository inventoryRepository,
                        OrderRepository orderRepository) {
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Tạo order từ cart trong một transaction atomic.
     * Trừ stock + tạo order + order items — tất cả hoặc không gì cả.
     * Optimistic lock trên Inventory kích hoạt khi flush.
     */
    @Transactional
    public Order createOrder(UUID userId, Cart cart, String shippingAddress) {
        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .shippingAddress(shippingAddress)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Inventory inventory = inventoryRepository.findByProductId(cartItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory not found for product: " + cartItem.getProductId()));

            if (inventory.getStockQuantity() < cartItem.getQuantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product: " + cartItem.getProductName() +
                        ". Available: " + inventory.getStockQuantity() +
                        ", requested: " + cartItem.getQuantity());
            }

            inventory.setStockQuantity(inventory.getStockQuantity() - cartItem.getQuantity());

            OrderItem orderItem = OrderItem.builder()
                    .productId(cartItem.getProductId())
                    .productName(cartItem.getProductName())
                    .unitPrice(cartItem.getPrice())
                    .quantity(cartItem.getQuantity())
                    .build();
            order.addItem(orderItem);

            totalAmount = totalAmount.add(
                    cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        return orderRepository.save(order);
    }

    @Transactional
public OrderResponse updateStatus(UUID orderId, OrderStatus newStatus) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

    OrderStatus current = order.getStatus();

    if (!current.canTransitionTo(newStatus)) {
        throw new BusinessException("INVALID_STATUS_TRANSITION",
                "Cannot transition order from " + current + " to " + newStatus + ".");
    }

    order.setStatus(newStatus);
    return OrderResponse.from(order);
}
}