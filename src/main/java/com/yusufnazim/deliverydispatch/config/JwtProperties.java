package com.yusufnazim.deliverydispatch.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration expiration
) {

    public JwtProperties {
        if (expiration != null && (expiration.isZero() || expiration.isNegative())) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
    }
}
