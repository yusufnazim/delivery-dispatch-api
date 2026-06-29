package com.yusufnazim.deliverydispatch.order.exception;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("Customer not found: " + customerId);
    }
}
