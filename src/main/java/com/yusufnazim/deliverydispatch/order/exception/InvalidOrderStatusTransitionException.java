package com.yusufnazim.deliverydispatch.order.exception;

import com.yusufnazim.deliverydispatch.order.OrderStatus;

public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(OrderStatus currentStatus, OrderStatus nextStatus) {
        super("Order cannot transition from " + currentStatus + " to " + nextStatus);
    }
}
