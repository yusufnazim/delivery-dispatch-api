package com.yusufnazim.deliverydispatch.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.user.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AdminCreateUserRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void dispatcherRoleHasNoViolations() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "dispatcher@example.com",
                "StrongPass123",
                Role.DISPATCHER);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void courierRoleHasNoViolations() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "courier@example.com",
                "StrongPass123",
                Role.COURIER);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void customerRoleIsRejected() {
        AdminCreateUserRequest request = validRequest(Role.CUSTOMER);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("managedUserRole");
    }

    @Test
    void adminRoleIsRejected() {
        AdminCreateUserRequest request = validRequest(Role.ADMIN);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("managedUserRole");
    }

    @Test
    void nullRoleIsRejected() {
        AdminCreateUserRequest request = validRequest(null);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("role");
    }

    @Test
    void blankEmailIsRejected() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(" ", "StrongPass123", Role.COURIER);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("email");
    }

    @Test
    void invalidEmailIsRejected() {
        AdminCreateUserRequest request = new AdminCreateUserRequest("not-an-email", "StrongPass123", Role.COURIER);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("email");
    }

    @Test
    void blankPasswordIsRejected() {
        AdminCreateUserRequest request = new AdminCreateUserRequest("courier@example.com", " ", Role.COURIER);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("password");
    }

    @Test
    void shortPasswordIsRejected() {
        AdminCreateUserRequest request = new AdminCreateUserRequest("courier@example.com", "short", Role.COURIER);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("password");
    }

    @Test
    void longPasswordIsRejected() {
        String longPassword = "a".repeat(73);
        AdminCreateUserRequest request = new AdminCreateUserRequest("courier@example.com", longPassword, Role.COURIER);

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("password");
    }

    private AdminCreateUserRequest validRequest(Role role) {
        return new AdminCreateUserRequest("managed@example.com", "StrongPass123", role);
    }

    private Set<String> violatedProperties(Set<ConstraintViolation<AdminCreateUserRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
