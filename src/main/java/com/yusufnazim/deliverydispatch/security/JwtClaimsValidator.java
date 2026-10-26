package com.yusufnazim.deliverydispatch.security;

import com.yusufnazim.deliverydispatch.user.Role;
import java.util.Arrays;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

public final class JwtClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_CLAIMS = new OAuth2Error(
            "invalid_token", "Required JWT identity claims or expiration are missing or invalid", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object userId = jwt.getClaims().get("userId");
        Object role = jwt.getClaims().get("role");
        Object subject = jwt.getClaims().get(JwtClaimNames.SUB);

        if (!isPositiveUserId(userId)
                || !isKnownRole(role)
                || !(subject instanceof String value)
                || value.isBlank()
                || jwt.getExpiresAt() == null) {
            return OAuth2TokenValidatorResult.failure(INVALID_CLAIMS);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean isPositiveUserId(Object value) {
        // Accept JSON integers only; Number.longValue() alone truncates fractions and overflows.
        return (value instanceof Long || value instanceof Integer) && ((Number) value).longValue() > 0;
    }

    private boolean isKnownRole(Object value) {
        return value instanceof String role
                && Arrays.stream(Role.values()).anyMatch(known -> known.name().equals(role));
    }
}
