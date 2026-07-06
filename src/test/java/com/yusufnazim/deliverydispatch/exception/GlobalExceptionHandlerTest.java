package com.yusufnazim.deliverydispatch.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.auth.exception.EmailAlreadyRegisteredException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidLoginCredentialsException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidManagedUserRoleException;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.courier.exception.InvalidCourierAvailabilityStatusException;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierAlreadyHasActiveDeliveryException;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierNotEligibleForDispatchException;
import com.yusufnazim.deliverydispatch.dispatch.exception.NoEligibleCourierException;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.exception.CustomerNotFoundException;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentNotAllowedException;
import com.yusufnazim.deliverydispatch.order.exception.OrderCancellationNotAllowedException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @MethodSource("domainErrors")
    void mapsDomainExceptionsToStableErrorCodes(DomainErrorCase errorCase) {
        ApiErrorResponse response = errorCase.invoke(handler);

        assertThat(response.code()).isEqualTo(errorCase.expectedCode());
        assertThat(response.message()).isEqualTo(errorCase.exception().getMessage());
    }

    @ParameterizedTest
    @MethodSource("domainErrors")
    void mapsDomainExceptionsToExpectedHttpStatuses(DomainErrorCase errorCase) {
        ResponseStatus responseStatus = errorCase.handlerMethod().getAnnotation(ResponseStatus.class);

        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value()).isEqualTo(errorCase.expectedStatus());
    }

    private static Stream<DomainErrorCase> domainErrors() throws NoSuchMethodException {
        return Stream.of(
                new DomainErrorCase(
                        method("handleEmailAlreadyRegistered", EmailAlreadyRegisteredException.class),
                        new EmailAlreadyRegisteredException("customer@example.com"),
                        "EMAIL_ALREADY_REGISTERED",
                        HttpStatus.CONFLICT),
                new DomainErrorCase(
                        method("handleInvalidLoginCredentials", InvalidLoginCredentialsException.class),
                        new InvalidLoginCredentialsException(),
                        "INVALID_LOGIN_CREDENTIALS",
                        HttpStatus.UNAUTHORIZED),
                new DomainErrorCase(
                        method("handleInvalidManagedUserRole", InvalidManagedUserRoleException.class),
                        new InvalidManagedUserRoleException(Role.CUSTOMER),
                        "INVALID_MANAGED_USER_ROLE",
                        HttpStatus.BAD_REQUEST),
                new DomainErrorCase(
                        method("handleCustomerNotFound", CustomerNotFoundException.class),
                        new CustomerNotFoundException(7L),
                        "CUSTOMER_NOT_FOUND",
                        HttpStatus.NOT_FOUND),
                new DomainErrorCase(
                        method("handleOrderNotFound", OrderNotFoundException.class),
                        new OrderNotFoundException(11L),
                        "ORDER_NOT_FOUND",
                        HttpStatus.NOT_FOUND),
                new DomainErrorCase(
                        method("handleOrderCancellationNotAllowed", OrderCancellationNotAllowedException.class),
                        new OrderCancellationNotAllowedException(OrderStatus.ASSIGNED),
                        "ORDER_CANCELLATION_NOT_ALLOWED",
                        HttpStatus.CONFLICT),
                new DomainErrorCase(
                        method("handleOrderAssignmentNotAllowed", OrderAssignmentNotAllowedException.class),
                        new OrderAssignmentNotAllowedException(OrderStatus.CANCELLED),
                        "ORDER_ASSIGNMENT_NOT_ALLOWED",
                        HttpStatus.CONFLICT),
                new DomainErrorCase(
                        method("handleNoEligibleCourier", NoEligibleCourierException.class),
                        new NoEligibleCourierException(15L),
                        "NO_ELIGIBLE_COURIER",
                        HttpStatus.CONFLICT),
                new DomainErrorCase(
                        method(
                                "handleCourierNotEligibleForDispatch",
                                CourierNotEligibleForDispatchException.class),
                        new CourierNotEligibleForDispatchException(9L, CourierAvailabilityStatus.UNAVAILABLE),
                        "COURIER_NOT_ELIGIBLE_FOR_DISPATCH",
                        HttpStatus.CONFLICT),
                new DomainErrorCase(
                        method(
                                "handleCourierAlreadyHasActiveDelivery",
                                CourierAlreadyHasActiveDeliveryException.class),
                        new CourierAlreadyHasActiveDeliveryException(9L),
                        "COURIER_ALREADY_HAS_ACTIVE_DELIVERY",
                        HttpStatus.CONFLICT),
                new DomainErrorCase(
                        method("handleCourierNotFound", CourierNotFoundException.class),
                        new CourierNotFoundException(9L),
                        "COURIER_NOT_FOUND",
                        HttpStatus.NOT_FOUND),
                new DomainErrorCase(
                        method(
                                "handleInvalidCourierAvailabilityStatus",
                                InvalidCourierAvailabilityStatusException.class),
                        new InvalidCourierAvailabilityStatusException(CourierAvailabilityStatus.ON_DELIVERY),
                        "INVALID_COURIER_AVAILABILITY_STATUS",
                        HttpStatus.BAD_REQUEST)
        );
    }

    private static Method method(String name, Class<?> parameterType) throws NoSuchMethodException {
        return GlobalExceptionHandler.class.getMethod(name, parameterType);
    }

    private record DomainErrorCase(
            Method handlerMethod,
            RuntimeException exception,
            String expectedCode,
            HttpStatus expectedStatus
    ) {

        ApiErrorResponse invoke(GlobalExceptionHandler handler) {
            try {
                return (ApiErrorResponse) handlerMethod.invoke(handler, exception);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
