package com.yusufnazim.deliverydispatch.courier.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record UpdateCourierAvailabilityRequest(
        @NotNull CourierAvailabilityStatus status
) {

    @JsonIgnore
    @AssertTrue(message = "status must be AVAILABLE or UNAVAILABLE")
    public boolean isSelfManagedStatus() {
        return status == null
                || status == CourierAvailabilityStatus.AVAILABLE
                || status == CourierAvailabilityStatus.UNAVAILABLE;
    }
}
