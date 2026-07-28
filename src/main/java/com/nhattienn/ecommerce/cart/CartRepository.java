package com.nhattienn.ecommerce.cart;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CartRepository {

    private static final String KEY_PREFIX = "cart:";
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Cart> cartRedisTemplate;

    public CartRepository(RedisTemplate<String, Cart> cartRedisTemplate) {
        this.cartRedisTemplate = cartRedisTemplate;
    }

    public Optional<Cart> findByUserId(UUID userId) {
        Cart cart = cartRedisTemplate.opsForValue().get(key(userId));
        return Optional.ofNullable(cart);
    }

    public void save(Cart cart) {
        cartRedisTemplate.opsForValue().set(key(cart.getUserId()), cart, TTL);
    }

    public void deleteByUserId(UUID userId) {
        cartRedisTemplate.delete(key(userId));
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}