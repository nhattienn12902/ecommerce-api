package com.nhattienn.ecommerce.cart;

import com.nhattienn.ecommerce.cart.dto.AddToCartRequest;
import com.nhattienn.ecommerce.cart.dto.CartResponse;
import com.nhattienn.ecommerce.cart.dto.UpdateCartItemRequest;
import com.nhattienn.ecommerce.common.exception.BusinessException;
import com.nhattienn.ecommerce.common.exception.ResourceNotFoundException;
import com.nhattienn.ecommerce.product.Product;
import com.nhattienn.ecommerce.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    public CartResponse getCart(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> emptyCart(userId));
        return CartResponse.from(cart);
    }

    public CartResponse addItem(UUID userId, AddToCartRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + request.productId()));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> emptyCart(userId));

        CartItem item = CartItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .quantity(request.quantity())
                .build();

        cart.addOrUpdateItem(item);
        cartRepository.save(cart);

        return CartResponse.from(cart);
    }

    public CartResponse updateItem(UUID userId, Long productId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found."));

        boolean itemExists = cart.getItems().stream()
                .anyMatch(i -> i.getProductId().equals(productId));

        if (!itemExists) {
            throw new ResourceNotFoundException("Product not found in cart: " + productId);
        }

        cart.updateItemQuantity(productId, request.quantity());
        cartRepository.save(cart);

        return CartResponse.from(cart);
    }

    public CartResponse removeItem(UUID userId, Long productId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found."));

        cart.removeItem(productId);
        cartRepository.save(cart);

        return CartResponse.from(cart);
    }

    public void clearCart(UUID userId) {
        cartRepository.deleteByUserId(userId);
    }

    private Cart emptyCart(UUID userId) {
        return Cart.builder()
                .userId(userId)
                .updatedAt(Instant.now())
                .build();
    }
}