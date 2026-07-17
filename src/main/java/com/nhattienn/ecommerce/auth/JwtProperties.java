package com.nhattienn.ecommerce.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank
        String secret,

        @NotNull
        Duration accessTokenTtl,

        @NotNull
        Duration refreshTokenTtl
) {
}