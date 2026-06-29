package com.yusufnazim.deliverydispatch.order.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryOrderResponseTest {

    @Test
    void mapsFromDeliveryOrder() {
        DeliveryOrder order = org.mockito.Mockito.mock(DeliveryOrder.class);
        Instant createdAt = Instant.parse("2026-06-29T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-29T10:05:00Z");
        when(order.getId()).thenReturn(7L);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getPickupAddress()).thenReturn("Istiklal Cd. No:1, Beyoglu");
        when(order.getPickupLatitude()).thenReturn(new BigDecimal("41.036900"));
        when(order.getPickupLongitude()).thenReturn(new BigDecimal("28.985000"));
        when(order.getDropoffAddress()).thenReturn("Bagdat Cd. No:10, Kadikoy");
        when(order.getDropoffLatitude()).thenReturn(new BigDecimal("40.970000"));
        when(order.getDropoffLongitude()).thenReturn(new BigDecimal("29.057000"));
        when(order.getCreatedAt()).thenReturn(createdAt);
        when(order.getUpdatedAt()).thenReturn(updatedAt);

        DeliveryOrderResponse response = DeliveryOrderResponse.from(order);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.pickupAddress()).isEqualTo("Istiklal Cd. No:1, Beyoglu");
        assertThat(response.pickupLatitude()).isEqualByComparingTo("41.036900");
        assertThat(response.pickupLongitude()).isEqualByComparingTo("28.985000");
        assertThat(response.dropoffAddress()).isEqualTo("Bagdat Cd. No:10, Kadikoy");
        assertThat(response.dropoffLatitude()).isEqualByComparingTo("40.970000");
        assertThat(response.dropoffLongitude()).isEqualByComparingTo("29.057000");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }
}
