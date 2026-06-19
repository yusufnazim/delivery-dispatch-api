package com.yusufnazim.deliverydispatch.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.yusufnazim.deliverydispatch.config.JwtProperties;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenServiceTest {

    private static final String SECRET = "test-jwt-token-generation-secret-32";

    private final JwtProperties jwtProperties = new JwtProperties(
            "delivery-dispatch-api-test",
            SECRET,
            Duration.ofMinutes(15));
    private final SecretKey secretKey = new SecretKeySpec(
            SECRET.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");
    private final JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    private final JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    private final JwtTokenService jwtTokenService = new JwtTokenService(jwtEncoder, jwtProperties);

    @Test
    void generateTokenSignsUserIdentityClaims() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.getEmail()).thenReturn("courier@example.com");
        when(user.getRole()).thenReturn(Role.COURIER);
        Instant beforeGeneration = Instant.now();

        String token = jwtTokenService.generateToken(user);

        Jwt jwt = jwtDecoder.decode(token);
        Instant afterGeneration = Instant.now();

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("delivery-dispatch-api-test");
        assertThat(jwt.getSubject()).isEqualTo("courier@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("COURIER");
        assertThat(jwt.<Number>getClaim("userId").longValue()).isEqualTo(42L);
        assertThat(jwt.getIssuedAt()).isBetween(
                beforeGeneration.minusSeconds(1),
                afterGeneration.plusSeconds(1));
        assertThat(jwt.getExpiresAt()).isBetween(
                beforeGeneration.plus(jwtProperties.expiration()).minusSeconds(1),
                afterGeneration.plus(jwtProperties.expiration()).plusSeconds(1));
    }
}
