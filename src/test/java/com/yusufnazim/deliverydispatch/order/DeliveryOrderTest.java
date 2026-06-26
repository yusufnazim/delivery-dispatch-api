package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

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
