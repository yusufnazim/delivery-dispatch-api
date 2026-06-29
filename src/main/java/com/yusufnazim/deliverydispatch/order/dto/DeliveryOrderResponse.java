package com.yusufnazim.deliverydispatch.order.dto;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record DeliveryOrderResponse(
        Long id,
        OrderStatus status,
        String pickupAddress,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String dropoffAddress,
        BigDecimal dropoffLatitude,
        BigDecimal dropoffLongitude,
        Instant createdAt,
        Instant updatedAt
) {

    public static DeliveryOrderResponse from(DeliveryOrder order) {
        return new DeliveryOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getPickupAddress(),
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                order.getDropoffAddress(),
                order.getDropoffLatitude(),
                order.getDropoffLongitude(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
