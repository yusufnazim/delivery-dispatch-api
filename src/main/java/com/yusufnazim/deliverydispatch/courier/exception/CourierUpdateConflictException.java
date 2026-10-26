package com.yusufnazim.deliverydispatch.courier.exception;

public class CourierUpdateConflictException extends RuntimeException {

    public CourierUpdateConflictException(Long courierId, Throwable cause) {
        super("Courier or delivery state changed concurrently; reload before retrying: " + courierId, cause);
    }
}
