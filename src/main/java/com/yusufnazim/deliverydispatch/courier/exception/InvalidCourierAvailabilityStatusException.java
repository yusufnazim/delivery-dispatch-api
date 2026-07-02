package com.yusufnazim.deliverydispatch.courier.exception;

import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;

public class InvalidCourierAvailabilityStatusException extends RuntimeException {

    public InvalidCourierAvailabilityStatusException(CourierAvailabilityStatus status) {
        super("Courier availability status cannot be self-managed: " + status);
    }
}
