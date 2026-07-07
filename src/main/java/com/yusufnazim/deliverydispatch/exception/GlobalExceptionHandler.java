package com.yusufnazim.deliverydispatch.exception;

import com.yusufnazim.deliverydispatch.auth.exception.EmailAlreadyRegisteredException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidManagedUserRoleException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidLoginCredentialsException;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.courier.exception.InvalidCourierAvailabilityStatusException;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierAlreadyHasActiveDeliveryException;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierNotEligibleForDispatchException;
import com.yusufnazim.deliverydispatch.dispatch.exception.NoEligibleCourierException;
import com.yusufnazim.deliverydispatch.order.exception.CustomerNotFoundException;
import com.yusufnazim.deliverydispatch.order.exception.InvalidOrderStatusTransitionException;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentConflictException;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentNotAllowedException;
import com.yusufnazim.deliverydispatch.order.exception.OrderCancellationNotAllowedException;
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

	@ExceptionHandler(OrderCancellationNotAllowedException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiErrorResponse handleOrderCancellationNotAllowed(OrderCancellationNotAllowedException exception) {
		return new ApiErrorResponse("ORDER_CANCELLATION_NOT_ALLOWED", exception.getMessage());
	}

	@ExceptionHandler(OrderAssignmentNotAllowedException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiErrorResponse handleOrderAssignmentNotAllowed(OrderAssignmentNotAllowedException exception) {
		return new ApiErrorResponse("ORDER_ASSIGNMENT_NOT_ALLOWED", exception.getMessage());
	}

	@ExceptionHandler(OrderAssignmentConflictException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiErrorResponse handleOrderAssignmentConflict(OrderAssignmentConflictException exception) {
		return new ApiErrorResponse("ORDER_ASSIGNMENT_CONFLICT", exception.getMessage());
	}

	@ExceptionHandler(InvalidOrderStatusTransitionException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiErrorResponse handleInvalidOrderStatusTransition(InvalidOrderStatusTransitionException exception) {
		return new ApiErrorResponse("INVALID_ORDER_STATUS_TRANSITION", exception.getMessage());
	}

	@ExceptionHandler(NoEligibleCourierException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiErrorResponse handleNoEligibleCourier(NoEligibleCourierException exception) {
		return new ApiErrorResponse("NO_ELIGIBLE_COURIER", exception.getMessage());
	}

	@ExceptionHandler(CourierNotEligibleForDispatchException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiErrorResponse handleCourierNotEligibleForDispatch(CourierNotEligibleForDispatchException exception) {
		return new ApiErrorResponse("COURIER_NOT_ELIGIBLE_FOR_DISPATCH", exception.getMessage());
	}

	@ExceptionHandler(CourierAlreadyHasActiveDeliveryException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiErrorResponse handleCourierAlreadyHasActiveDelivery(CourierAlreadyHasActiveDeliveryException exception) {
		return new ApiErrorResponse("COURIER_ALREADY_HAS_ACTIVE_DELIVERY", exception.getMessage());
	}

	@ExceptionHandler(CourierNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiErrorResponse handleCourierNotFound(CourierNotFoundException exception) {
		return new ApiErrorResponse("COURIER_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(InvalidCourierAvailabilityStatusException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiErrorResponse handleInvalidCourierAvailabilityStatus(
			InvalidCourierAvailabilityStatusException exception) {
		return new ApiErrorResponse("INVALID_COURIER_AVAILABILITY_STATUS", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiErrorResponse handleValidationFailure() {
		return new ApiErrorResponse("VALIDATION_FAILED", "Request validation failed");
	}
}
