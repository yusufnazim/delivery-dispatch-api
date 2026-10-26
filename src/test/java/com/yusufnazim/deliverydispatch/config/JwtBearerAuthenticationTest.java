package com.yusufnazim.deliverydispatch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.yusufnazim.deliverydispatch.security.JwtTokenService;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
class JwtBearerAuthenticationTest {

    private final MockMvc mockMvc;
    private final JwtTokenService jwtTokenService;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;

    @Autowired
    JwtBearerAuthenticationTest(
            MockMvc mockMvc, JwtTokenService jwtTokenService, JwtDecoder jwtDecoder, JwtProperties jwtProperties) {
        this.mockMvc = mockMvc;
        this.jwtTokenService = jwtTokenService;
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
    }

    @Test
    void protectedEndpointReturnsJsonForMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void protectedEndpointReturnsJsonForInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void protectedEndpointAcceptsValidBearerToken(Role role) throws Exception {
        String token = jwtTokenService.generateToken(user(role));

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("protected"));
    }

    @Test
    void roleProtectedEndpointAcceptsMatchingRoleClaim() throws Exception {
        String token = jwtTokenService.generateToken(user(Role.CUSTOMER));

        mockMvc.perform(get("/api/v1/test/customer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("customer"));
    }

    @Test
    void roleProtectedEndpointReturnsJsonForDifferentRoleClaim() throws Exception {
        String token = jwtTokenService.generateToken(user(Role.COURIER));

        mockMvc.perform(get("/api/v1/test/customer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "userId", "role", "sub", "exp"})
    void rejectsSignedTokenWithMissingRequiredClaim(String claim) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.remove(claim);

        assertRejected(sign(claims));
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void rejectsSignedTokenWithInvalidClaim(String claim, Object value) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put(claim, value);

        assertRejected(sign(claims));
    }

    private static Stream<Arguments> invalidClaims() {
        return Stream.of(
                Arguments.of("iss", "another-application"),
                Arguments.of("iss", "delivery-dispatch-api"),
                Arguments.of("iss", ""),
                Arguments.of("iss", null),
                Arguments.of("iss", List.of("delivery-dispatch-api-test")),
                Arguments.of("userId", null),
                Arguments.of("userId", "1"),
                Arguments.of("userId", 0),
                Arguments.of("userId", -1),
                Arguments.of("userId", 1.5),
                Arguments.of("userId", 1.0),
                Arguments.of("userId", new BigInteger("9223372036854775808")),
                Arguments.of("userId", new BigInteger("18446744073709551617")),
                Arguments.of("userId", 1e30),
                Arguments.of("userId", true),
                Arguments.of("userId", List.of(1)),
                Arguments.of("userId", Map.of("id", 1)),
                Arguments.of("role", null),
                Arguments.of("role", ""),
                Arguments.of("role", " "),
                Arguments.of("role", "customer"),
                Arguments.of("role", "OWNER"),
                Arguments.of("role", "ROLE_ADMIN"),
                Arguments.of("role", 1),
                Arguments.of("role", true),
                Arguments.of("role", List.of("ADMIN")),
                Arguments.of("role", Map.of("name", "ADMIN")),
                Arguments.of("sub", null),
                Arguments.of("sub", " "),
                Arguments.of("sub", List.of("customer@example.com")),
                Arguments.of("exp", null),
                Arguments.of("exp", "not-a-timestamp"));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2147483648L, Long.MAX_VALUE})
    void acceptsPositiveIntegerUserIdsWithoutNarrowingThem(long userId) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("userId", userId);
        String token = sign(claims);

        assertThat(jwtDecoder.decode(token).<Number>getClaim("userId").longValue())
                .isEqualTo(userId);
        mockMvc.perform(get("/api/v1/test/customer").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void stillRejectsExpiredToken() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("exp", Instant.now().minusSeconds(300).getEpochSecond());

        assertRejected(sign(claims));
    }

    @Test
    void stillRejectsTokenBeforeItsNotBeforeTime() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("nbf", Instant.now().plusSeconds(300).getEpochSecond());

        assertRejected(sign(claims));
    }

    @Test
    void stillRejectsTokenSignedWithDifferentKey() throws Exception {
        assertRejected(sign(validClaims(), "different-test-signing-secret-at-least-32-characters"));
    }

    @Test
    void stillRejectsTokenWithTamperedRole() throws Exception {
        Map<String, Object> claims = validClaims();
        JWSObject original = JWSObject.parse(sign(claims));
        claims.put("role", "ADMIN");
        JWSObject tampered = new JWSObject(original.getHeader().toBase64URL(),
                new Payload(claims).toBase64URL(), original.getSignature());

        assertRejected(tampered.serialize());
    }

    @Test
    void stillRejectsUnsignedToken() throws Exception {
        assertRejected(new PlainJWT(JWTClaimsSet.parse(validClaims())).serialize());
    }

    private Map<String, Object> validClaims() {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", jwtProperties.issuer());
        claims.put("sub", "customer@example.com");
        claims.put("userId", 1L);
        claims.put("role", "CUSTOMER");
        claims.put("iat", now.minusSeconds(600).getEpochSecond());
        claims.put("exp", now.plusSeconds(900).getEpochSecond());
        return claims;
    }

    private String sign(Map<String, Object> claims) throws Exception {
        return sign(claims, jwtProperties.secret());
    }

    private String sign(Map<String, Object> claims, String secret) throws Exception {
        // Sign the raw JSON claims so test helpers cannot coerce malformed values into valid ones.
        JWSObject token = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(claims));
        token.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }

    private void assertRejected(String token) throws Exception {
        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
        mockMvc.perform(get("/api/v1/test/customer").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("""
                        {"code":"AUTHENTICATION_REQUIRED","message":"Authentication is required"}
                        """));
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private User user(Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("customer@example.com");
        when(user.getRole()).thenReturn(role);
        return user;
    }

    @TestConfiguration
    static class ProtectedEndpointConfig {

        @Bean
        ProtectedTestController protectedTestController() {
            return new ProtectedTestController();
        }
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/v1/test/protected")
        String protectedEndpoint() {
            return "protected";
        }

        @PreAuthorize("hasRole('CUSTOMER')")
        @GetMapping("/api/v1/test/customer")
        String customerEndpoint() {
            return "customer";
        }
    }
}
