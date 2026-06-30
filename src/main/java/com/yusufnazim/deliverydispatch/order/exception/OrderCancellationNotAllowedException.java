package com.yusufnazim.deliverydispatch.order.exception;

import com.yusufnazim.deliverydispatch.order.OrderStatus;

public class OrderCancellationNotAllowedException extends RuntimeException {

    public OrderCancellationNotAllowedException(OrderStatus status) {
        super("Order cannot be cancelled from status: " + status);
    }
}
