package com.yusufnazim.deliverydispatch.exception;

import com.yusufnazim.deliverydispatch.auth.exception.EmailAlreadyRegisteredException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidManagedUserRoleException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidLoginCredentialsException;
import com.yusufnazim.deliverydispatch.order.exception.CustomerNotFoundException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
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

	@ExceptionHandler(InvalidLoginCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ApiErrorResponse handleInvalidLoginCredentials(InvalidLoginCredentialsException exception) {
		return new ApiErrorResponse("INVALID_LOGIN_CREDENTIALS", exception.getMessage());
	}

	@ExceptionHandler(InvalidManagedUserRoleException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiErrorResponse handleInvalidManagedUserRole(InvalidManagedUserRoleException exception) {
		return new ApiErrorResponse("INVALID_MANAGED_USER_ROLE", exception.getMessage());
	}

	@ExceptionHandler(CustomerNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiErrorResponse handleCustomerNotFound(CustomerNotFoundException exception) {
		return new ApiErrorResponse("CUSTOMER_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(OrderNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiErrorResponse handleOrderNotFound(OrderNotFoundException exception) {
		return new ApiErrorResponse("ORDER_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiErrorResponse handleValidationFailure() {
		return new ApiErrorResponse("VALIDATION_FAILED", "Request validation failed");
	}
}
