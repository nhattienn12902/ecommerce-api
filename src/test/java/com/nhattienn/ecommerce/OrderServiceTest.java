package com.nhattienn.ecommerce;

import com.nhattienn.ecommerce.cart.Cart;
import com.nhattienn.ecommerce.cart.CartItem;
import com.nhattienn.ecommerce.common.exception.BusinessException;
import com.nhattienn.ecommerce.common.exception.InsufficientStockException;
import com.nhattienn.ecommerce.common.exception.ResourceNotFoundException;
import com.nhattienn.ecommerce.order.Order;
import com.nhattienn.ecommerce.order.OrderRepository;
import com.nhattienn.ecommerce.order.OrderService;
import com.nhattienn.ecommerce.order.OrderStatus;
import com.nhattienn.ecommerce.order.dto.OrderResponse;
import com.nhattienn.ecommerce.product.Inventory;
import com.nhattienn.ecommerce.product.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        // orderRepository.save trả về chính order được truyền vào —
        // để test có thể assert trên state của order sau khi build
        lenient().when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Cart cartWith(CartItem... items) {
        return Cart.builder()
                .userId(userId)
                .items(new ArrayList<>(List.of(items)))
                .updatedAt(Instant.now())
                .build();
    }

    private CartItem cartItem(Long productId, String name, String price, int qty) {
        return CartItem.builder()
                .productId(productId)
                .productName(name)
                .price(new BigDecimal(price))
                .quantity(qty)
                .build();
    }

    private Inventory inventoryWith(Long productId, int stock) {
        return Inventory.builder()
                .productId(productId)
                .stockQuantity(stock)
                .reservedQuantity(0)
                .version(0L)
                .build();
    }

    // -----------------------------------------------------------------------
    // createOrder()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("createOrder()")
    class CreateOrder {

        @Test
        @DisplayName("should create order and deduct stock on successful checkout")
        void shouldCreateOrderSuccessfully() {
            Cart cart = cartWith(cartItem(1L, "iPhone", "999.99", 2));
            Inventory inventory = inventoryWith(1L, 10);

            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));

            Order response = orderService.createOrder(userId, cart, "123 Address");

            // Stock bị trừ đúng: 10 - 2 = 8
            assertThat(inventory.getStockQuantity()).isEqualTo(8);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getItems()).hasSize(1);

            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("should throw InsufficientStockException when stock is not enough")
        void shouldThrowWhenStockInsufficient() {
            Cart cart = cartWith(cartItem(1L, "iPhone", "999.99", 5));
            Inventory inventory = inventoryWith(1L, 3); // chỉ có 3, cần 5

            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));

            assertThatThrownBy(() -> orderService.createOrder(userId, cart, "123 Address"))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock");

            // Stock không bị thay đổi khi throw
            assertThat(inventory.getStockQuantity()).isEqualTo(3);
            // Order không được tạo
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when inventory does not exist")
        void shouldThrowWhenInventoryNotFound() {
            Cart cart = cartWith(cartItem(99L, "Ghost Product", "10.00", 1));

            when(inventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(userId, cart, "123 Address"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should use cart snapshot price, not current product price")
        void shouldUseSnapshotPrice() {
            // Giá trong cart là 999.99 (snapshot lúc thêm vào giỏ)
            Cart cart = cartWith(cartItem(1L, "iPhone", "999.99", 1));
            Inventory inventory = inventoryWith(1L, 10);

            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));

            Order response = orderService.createOrder(userId, cart, "123 Address");

            // unitPrice trong order item phải là giá snapshot từ cart, không load lại từ product
            assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("999.99");
            // createOrder KHÔNG được query product để lấy giá hiện tại
            verifyNoInteractions(mock(com.nhattienn.ecommerce.product.ProductRepository.class));
        }

        @Test
        @DisplayName("should calculate total amount correctly for multiple items")
        void shouldCalculateTotalForMultipleItems() {
            Cart cart = cartWith(
                    cartItem(1L, "iPhone", "999.99", 2),      // 1999.98
                    cartItem(5L, "Clean Code", "39.99", 3));   // 119.97

            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventoryWith(1L, 10)));
            when(inventoryRepository.findByProductId(5L)).thenReturn(Optional.of(inventoryWith(5L, 10)));

            Order response = orderService.createOrder(userId, cart, "123 Address");

            // Total = 1999.98 + 119.97 = 2119.95
            assertThat(response.getTotalAmount()).isEqualByComparingTo("2119.95");
            assertThat(response.getItems()).hasSize(2);
        }
    }

    // -----------------------------------------------------------------------
    // updateStatus()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        private Order orderWithStatus(OrderStatus status) {
            return Order.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .status(status)
                    .totalAmount(new BigDecimal("100.00"))
                    .shippingAddress("123 Address")
                    .items(new ArrayList<>())
                    .build();
        }

        @Test
        @DisplayName("should update status on valid transition")
        void shouldUpdateOnValidTransition() {
            Order order = orderWithStatus(OrderStatus.PENDING);
            UUID orderId = order.getId();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.updateStatus(orderId, OrderStatus.CONFIRMED);

            assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("should throw when transition is invalid")
        void shouldThrowOnInvalidTransition() {
            Order order = orderWithStatus(OrderStatus.CONFIRMED);
            UUID orderId = order.getId();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // CONFIRMED → DELIVERED không hợp lệ (phải qua SHIPPED)
            assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.DELIVERED))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot transition");

            // Status không đổi
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("should throw when transitioning from terminal state")
        void shouldThrowFromTerminalState() {
            Order order = orderWithStatus(OrderStatus.DELIVERED);
            UUID orderId = order.getId();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // DELIVERED là terminal — không transition đi đâu được
            assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.SHIPPED))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot transition");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order does not exist")
        void shouldThrowWhenOrderNotFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.CONFIRMED))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}