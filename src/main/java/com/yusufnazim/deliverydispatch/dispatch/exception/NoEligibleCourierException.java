package com.yusufnazim.deliverydispatch.dispatch.exception;

public class NoEligibleCourierException extends RuntimeException {

    public NoEligibleCourierException(Long orderId) {
        super("No eligible courier found for order: " + orderId);
    }
}
