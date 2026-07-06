package com.yusufnazim.deliverydispatch.dispatch.exception;

import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;

public class CourierNotEligibleForDispatchException extends RuntimeException {

    public CourierNotEligibleForDispatchException(Long courierId, CourierAvailabilityStatus status) {
        super("Courier is not eligible for dispatch: " + courierId + " with status: " + status);
    }
}
