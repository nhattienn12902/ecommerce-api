package com.nhattienn.ecommerce.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class CartRedisConfig {

    @Bean
    public RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Cart> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisSerializer<Cart> cartSerializer = new RedisSerializer<>() {
            @Override
            public byte[] serialize(Cart cart) throws SerializationException {
                if (cart == null) return new byte[0];
                try {
                    return mapper.writeValueAsBytes(cart);
                } catch (Exception e) {
                    throw new SerializationException("Failed to serialize Cart", e);
                }
            }

            @Override
            public Cart deserialize(byte[] bytes) throws SerializationException {
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return mapper.readValue(bytes, Cart.class);
                } catch (Exception e) {
                    throw new SerializationException("Failed to deserialize Cart", e);
                }
            }
        };

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(cartSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(cartSerializer);

        return template;
    }
}