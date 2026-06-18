package com.yusufnazim.deliverydispatch.exception;

import com.yusufnazim.deliverydispatch.auth.exception.EmailAlreadyRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiErrorResponse handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
		return new ApiErrorResponse("EMAIL_ALREADY_REGISTERED", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiErrorResponse handleValidationFailure() {
		return new ApiErrorResponse("VALIDATION_FAILED", "Request validation failed");
	}
}
