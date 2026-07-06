package com.yusufnazim.deliverydispatch.order.exception;

import com.yusufnazim.deliverydispatch.order.OrderStatus;

public class OrderAssignmentNotAllowedException extends RuntimeException {

    public OrderAssignmentNotAllowedException(OrderStatus status) {
        super("Order cannot be assigned from status: " + status);
    }
}
