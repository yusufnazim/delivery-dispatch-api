package com.yusufnazim.deliverydispatch.courier.exception;

public class CourierAvailabilityChangeNotAllowedException extends RuntimeException {

    public CourierAvailabilityChangeNotAllowedException(Long courierId) {
        super("Courier availability cannot change during an active delivery: " + courierId);
    }
}
