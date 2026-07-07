package com.yusufnazim.deliverydispatch.order.exception;

public class OrderAssignmentConflictException extends RuntimeException {

    public OrderAssignmentConflictException(Long orderId) {
        super("Order assignment conflict for order: " + orderId);
    }

    public OrderAssignmentConflictException(Long orderId, Throwable cause) {
        super("Order assignment conflict for order: " + orderId, cause);
    }
}
