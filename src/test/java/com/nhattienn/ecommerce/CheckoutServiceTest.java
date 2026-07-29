package com.nhattienn.ecommerce;

import com.nhattienn.ecommerce.order.CheckoutService;
import com.nhattienn.ecommerce.order.Order;
import com.nhattienn.ecommerce.order.OrderService;
import com.nhattienn.ecommerce.order.OrderStatus;
import com.nhattienn.ecommerce.cart.Cart;
import com.nhattienn.ecommerce.cart.CartItem;
import com.nhattienn.ecommerce.cart.CartRepository;
import com.nhattienn.ecommerce.common.exception.BusinessException;
import com.nhattienn.ecommerce.common.exception.InsufficientStockException;
import com.nhattienn.ecommerce.order.dto.CheckoutRequest;
import com.nhattienn.ecommerce.order.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private OrderService orderService;

    @InjectMocks
    private CheckoutService checkoutService;

    private UUID userId;
    private CheckoutRequest request;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        request = new CheckoutRequest("123 Nguyen Hue, District 1");
    }

    private Cart nonEmptyCart() {
        return Cart.builder()
                .userId(userId)
                .items(new ArrayList<>(List.of(
                        CartItem.builder()
                                .productId(1L).productName("iPhone")
                                .price(new BigDecimal("999.99")).quantity(1)
                                .build())))
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("should throw when cart does not exist in Redis")
    void shouldThrowWhenCartNotFound() {
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.checkout(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cart is empty");

        // Không tạo order, không xóa cart
        verify(orderService, never()).createOrder(any(), any(), any());
        verify(cartRepository, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("should throw when cart is empty")
    void shouldThrowWhenCartIsEmpty() {
        Cart emptyCart = Cart.builder()
                .userId(userId)
                .items(new ArrayList<>())
                .updatedAt(Instant.now())
                .build();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(emptyCart));

        assertThatThrownBy(() -> checkoutService.checkout(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cart is empty");

        verify(orderService, never()).createOrder(any(), any(), any());
        verify(cartRepository, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("should create order then clear cart in correct order on success")
    void shouldCreateOrderThenClearCart() {
        Cart cart = nonEmptyCart();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("999.99"))
                .shippingAddress(request.shippingAddress())
                .items(new ArrayList<>())
                .build();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(orderService.createOrder(eq(userId), eq(cart), eq(request.shippingAddress())))
                .thenReturn(order);

        OrderResponse response = checkoutService.checkout(userId, request);

        // Verify THỨ TỰ: createOrder PHẢI được gọi TRƯỚC deleteByUserId
        // Đây là bảo vệ Quyết định 2 — cart chỉ xóa sau khi order tạo thành công
        InOrder inOrder = inOrder(orderService, cartRepository);
        inOrder.verify(orderService).createOrder(userId, cart, request.shippingAddress());
        inOrder.verify(cartRepository).deleteByUserId(userId);
    }

    @Test
    @DisplayName("should NOT clear cart when createOrder throws")
    void shouldNotClearCartWhenOrderCreationFails() {
        Cart cart = nonEmptyCart();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        // createOrder throw — mô phỏng stock không đủ giữa chừng
        when(orderService.createOrder(any(), any(), any()))
                .thenThrow(new InsufficientStockException("Insufficient stock"));

        assertThatThrownBy(() -> checkoutService.checkout(userId, request))
                .isInstanceOf(InsufficientStockException.class);

        // CART KHÔNG ĐƯỢC XÓA — đây là invariant quan trọng nhất
        // Nếu order tạo thất bại, user phải giữ nguyên giỏ hàng để thử lại
        verify(cartRepository, never()).deleteByUserId(any());
    }
}