package com.yusufnazim.deliverydispatch.courier.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UpdateCourierLocationRequestTest {

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
        UpdateCourierLocationRequest request = validRequest();

        Set<ConstraintViolation<UpdateCourierLocationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void missingLatitudeIsRejected() {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                null,
                new BigDecimal("28.978400"));

        Set<ConstraintViolation<UpdateCourierLocationRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("latitude");
    }

    @Test
    void missingLongitudeIsRejected() {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                new BigDecimal("41.008200"),
                null);

        Set<ConstraintViolation<UpdateCourierLocationRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("longitude");
    }

    @Test
    void latitudeOutsideRangeIsRejected() {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                new BigDecimal("91.000000"),
                new BigDecimal("28.978400"));

        Set<ConstraintViolation<UpdateCourierLocationRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("latitude");
    }

    @Test
    void longitudeOutsideRangeIsRejected() {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                new BigDecimal("41.008200"),
                new BigDecimal("181.000000"));

        Set<ConstraintViolation<UpdateCourierLocationRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("longitude");
    }

    @Test
    void coordinateWithTooManyFractionDigitsIsRejected() {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                new BigDecimal("41.0082001"),
                new BigDecimal("28.978400"));

        Set<ConstraintViolation<UpdateCourierLocationRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("latitude");
    }

    private UpdateCourierLocationRequest validRequest() {
        return new UpdateCourierLocationRequest(
                new BigDecimal("41.008200"),
                new BigDecimal("28.978400"));
    }

    private Set<String> violatedProperties(Set<ConstraintViolation<UpdateCourierLocationRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
