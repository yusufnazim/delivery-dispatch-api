package com.yusufnazim.deliverydispatch.auth.exception;

public class InvalidLoginCredentialsException extends RuntimeException {

    public InvalidLoginCredentialsException() {
        super("Invalid email or password");
    }
}
