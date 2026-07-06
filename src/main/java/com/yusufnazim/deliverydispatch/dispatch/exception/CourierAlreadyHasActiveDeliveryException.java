package com.yusufnazim.deliverydispatch.dispatch.exception;

public class CourierAlreadyHasActiveDeliveryException extends RuntimeException {

    public CourierAlreadyHasActiveDeliveryException(Long courierId) {
        super("Courier already has an active delivery: " + courierId);
    }
}
