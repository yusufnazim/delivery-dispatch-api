package com.yusufnazim.deliverydispatch.dispatch.dto;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.OrderStatus;

public record DispatchAssignmentResponse(
        Long orderId,
        Long courierId,
        OrderStatus status
) {

    public static DispatchAssignmentResponse from(DeliveryOrder order) {
        return new DispatchAssignmentResponse(
                order.getId(),
                order.getCourier().getId(),
                order.getStatus());
    }
}
