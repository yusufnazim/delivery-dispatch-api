package com.yusufnazim.deliverydispatch.timeline.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEvent;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventType;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeliveryEventResponseTest {

    @Test
    void fromMapsDeliveryEvent() {
        DeliveryEvent event = new DeliveryEvent(
                order(),
                DeliveryEventType.ORDER_PICKED_UP,
                "Order picked up");
        Instant createdAt = Instant.parse("2026-07-09T10:00:00Z");
        ReflectionTestUtils.setField(event, "createdAt", createdAt);

        DeliveryEventResponse response = DeliveryEventResponse.from(event);

        assertThat(response.type()).isEqualTo(DeliveryEventType.ORDER_PICKED_UP);
        assertThat(response.description()).isEqualTo("Order picked up");
        assertThat(response.createdAt()).isEqualTo(createdAt);
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
