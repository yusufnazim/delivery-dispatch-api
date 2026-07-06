package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentNotAllowedException;
import com.yusufnazim.deliverydispatch.order.exception.OrderCancellationNotAllowedException;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeliveryOrderTest {

    @Test
    void newOrderStartsPendingWithoutCourier() {
        DeliveryOrder order = order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCourier()).isNull();
    }

    @Test
    void createCallbackSetsTimestamps() {
        DeliveryOrder order = order();

        order.onCreate();

        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    void cancelMovesPendingOrderToCancelled() {
        DeliveryOrder order = order();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelRejectsAssignedOrder() {
        DeliveryOrder order = order();
        ReflectionTestUtils.setField(order, "status", OrderStatus.ASSIGNED);

        assertThatThrownBy(order::cancel)
                .isInstanceOf(OrderCancellationNotAllowedException.class)
                .hasMessage("Order cannot be cancelled from status: ASSIGNED");
    }

    @Test
    void assignCourierMovesPendingOrderToAssigned() {
        DeliveryOrder order = order();
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);

        order.assignCourier(courier);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order.getCourier()).isEqualTo(courier);
    }

    @Test
    void assignCourierRejectsNonPendingOrder() {
        DeliveryOrder order = order();
        order.cancel();
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);

        assertThatThrownBy(() -> order.assignCourier(courier))
                .isInstanceOf(OrderAssignmentNotAllowedException.class)
                .hasMessage("Order cannot be assigned from status: CANCELLED");
    }

    @Test
    void assignCourierRejectsAlreadyAssignedOrder() {
        DeliveryOrder order = order();
        User firstCourier = new User("first-courier@example.com", "hashed-password", Role.COURIER);
        User secondCourier = new User("second-courier@example.com", "hashed-password", Role.COURIER);
        order.assignCourier(firstCourier);

        assertThatThrownBy(() -> order.assignCourier(secondCourier))
                .isInstanceOf(OrderAssignmentNotAllowedException.class)
                .hasMessage("Order cannot be assigned from status: ASSIGNED");
        assertThat(order.getCourier()).isEqualTo(firstCourier);
    }

    private DeliveryOrder order() {
        User customer = new User("customer@example.com", "hashed-password", Role.CUSTOMER);
        return new DeliveryOrder(
                customer,
                "Pickup",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Dropoff",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }
}
