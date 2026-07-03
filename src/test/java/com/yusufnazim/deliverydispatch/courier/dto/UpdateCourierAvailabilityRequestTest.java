package com.yusufnazim.deliverydispatch.courier.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UpdateCourierAvailabilityRequestTest {

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
    void availableStatusHasNoViolations() {
        UpdateCourierAvailabilityRequest request =
                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.AVAILABLE);

        Set<ConstraintViolation<UpdateCourierAvailabilityRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void unavailableStatusHasNoViolations() {
        UpdateCourierAvailabilityRequest request =
                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.UNAVAILABLE);

        Set<ConstraintViolation<UpdateCourierAvailabilityRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void missingStatusIsRejected() {
        UpdateCourierAvailabilityRequest request = new UpdateCourierAvailabilityRequest(null);

        Set<ConstraintViolation<UpdateCourierAvailabilityRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("status");
    }

    @Test
    void onDeliveryStatusIsRejected() {
        UpdateCourierAvailabilityRequest request =
                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.ON_DELIVERY);

        Set<ConstraintViolation<UpdateCourierAvailabilityRequest>> violations = validator.validate(request);

        assertThat(violatedProperties(violations)).contains("selfManagedStatus");
    }

    private Set<String> violatedProperties(Set<ConstraintViolation<UpdateCourierAvailabilityRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
