package com.yusufnazim.deliverydispatch.order.dto;

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

class CreateDeliveryOrderRequestTest {

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
        CreateDeliveryOrderRequest request = validRequest();

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void blankPickupAddressIsRejected() {
        CreateDeliveryOrderRequest request = new CreateDeliveryOrderRequest(
                " ",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("pickupAddress");
    }

    @Test
    void blankDropoffAddressIsRejected() {
        CreateDeliveryOrderRequest request = new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                " ",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("dropoffAddress");
    }

    @Test
    void longAddressIsRejected() {
        String longAddress = "a".repeat(501);
        CreateDeliveryOrderRequest request = new CreateDeliveryOrderRequest(
                longAddress,
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("pickupAddress");
    }

    @Test
    void missingCoordinateIsRejected() {
        CreateDeliveryOrderRequest request = new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                null,
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("pickupLatitude");
    }

    @Test
    void latitudeOutsideRangeIsRejected() {
        CreateDeliveryOrderRequest request = new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("91.000000"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("pickupLatitude");
    }

    @Test
    void longitudeOutsideRangeIsRejected() {
        CreateDeliveryOrderRequest request = new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("181.000000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("pickupLongitude");
    }

    @Test
    void dropoffLatitudeOutsideRangeIsRejected() {
        CreateDeliveryOrderRequest request = new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("-91.000000"),
                new BigDecimal("29.057000"));

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("dropoffLatitude");
    }

    @Test
    void coordinateWithTooManyFractionDigitsIsRejected() {
        CreateDeliveryOrderRequest request = new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.0369001"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("pickupLatitude");
    }

    private CreateDeliveryOrderRequest validRequest() {
        return new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }

    private Set<String> violatedProperties(Set<ConstraintViolation<CreateDeliveryOrderRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
