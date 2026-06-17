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

class RegisterCustomerRequestTest {

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
		RegisterCustomerRequest request = new RegisterCustomerRequest(
				"customer@example.com",
				"StrongPass123");

		Set<ConstraintViolation<RegisterCustomerRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

	@Test
	void blankEmailIsRejected() {
		RegisterCustomerRequest request = new RegisterCustomerRequest(" ", "StrongPass123");

		Set<ConstraintViolation<RegisterCustomerRequest>> violations = validator.validate(request);

		assertThat(violatedProperties(violations)).contains("email");
	}

	@Test
	void invalidEmailIsRejected() {
		RegisterCustomerRequest request = new RegisterCustomerRequest("not-an-email", "StrongPass123");

		Set<ConstraintViolation<RegisterCustomerRequest>> violations = validator.validate(request);

		assertThat(violatedProperties(violations)).contains("email");
	}

	@Test
	void shortPasswordIsRejected() {
		RegisterCustomerRequest request = new RegisterCustomerRequest("customer@example.com", "short");

		Set<ConstraintViolation<RegisterCustomerRequest>> violations = validator.validate(request);

		assertThat(violatedProperties(violations)).contains("password");
	}

	@Test
	void longPasswordIsRejected() {
		String longPassword = "a".repeat(73);
		RegisterCustomerRequest request = new RegisterCustomerRequest("customer@example.com", longPassword);

		Set<ConstraintViolation<RegisterCustomerRequest>> violations = validator.validate(request);

		assertThat(violatedProperties(violations)).contains("password");
	}

	private Set<String> violatedProperties(Set<ConstraintViolation<RegisterCustomerRequest>> violations) {
		return violations.stream()
				.map(violation -> violation.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}
}
