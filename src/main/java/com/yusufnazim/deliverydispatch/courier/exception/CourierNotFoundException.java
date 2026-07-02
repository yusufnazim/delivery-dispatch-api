package com.yusufnazim.deliverydispatch.courier.exception;

public class CourierNotFoundException extends RuntimeException {

    public CourierNotFoundException(Long courierId) {
        super("Courier not found: " + courierId);
    }
}
