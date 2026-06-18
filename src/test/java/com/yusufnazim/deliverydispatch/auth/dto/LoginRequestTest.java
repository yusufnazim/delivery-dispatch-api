package com.yusufnazim.deliverydispatch.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoginRequestTest {

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
	void validRequestHasNoViolations() {
		LoginRequest request = new LoginRequest(
				"customer@example.com",
				"StrongPass123");

		Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

	@Test
	void blankEmailIsRejected() {
		LoginRequest request = new LoginRequest(" ", "StrongPass123");

		Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

		assertThat(violatedProperties(violations)).contains("email");
	}

	@Test
	void invalidEmailIsRejected() {
		LoginRequest request = new LoginRequest("not-an-email", "StrongPass123");

		Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

		assertThat(violatedProperties(violations)).contains("email");
	}

	@Test
	void blankPasswordIsRejected() {
		LoginRequest request = new LoginRequest("customer@example.com", " ");

		Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

		assertThat(violatedProperties(violations)).contains("password");
	}

	@Test
	void longPasswordIsRejected() {
		String longPassword = "a".repeat(73);
		LoginRequest request = new LoginRequest("customer@example.com", longPassword);

		Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

		assertThat(violatedProperties(violations)).contains("password");
	}

	private Set<String> violatedProperties(Set<ConstraintViolation<LoginRequest>> violations) {
		return violations.stream()
				.map(violation -> violation.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}
}
