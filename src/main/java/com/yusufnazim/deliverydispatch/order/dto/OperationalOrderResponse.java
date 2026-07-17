package com.yusufnazim.deliverydispatch.order.dto;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;
import java.time.Instant;

public record OperationalOrderResponse(
        Long id,
        OrderStatus status,
        Long customerId,
        String customerEmail,
        Long courierId,
        String courierEmail,
        String courierDisplayName,
        String pickupAddress,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String dropoffAddress,
        BigDecimal dropoffLatitude,
        BigDecimal dropoffLongitude,
        Instant createdAt,
        Instant updatedAt
) {

    public static OperationalOrderResponse from(DeliveryOrder order) {
        User courier = order.getCourier();
        return new OperationalOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCustomer().getId(),
                order.getCustomer().getEmail(),
                courier == null ? null : courier.getId(),
                courier == null ? null : courier.getEmail(),
                courier == null ? null : courier.getCourierDisplayName(),
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
