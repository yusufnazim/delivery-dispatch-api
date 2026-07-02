package com.yusufnazim.deliverydispatch.courier.dto;

import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.User;

public record CourierAvailabilityResponse(
        Long courierId,
        CourierAvailabilityStatus status
) {

    public static CourierAvailabilityResponse from(User courier) {
        return new CourierAvailabilityResponse(
                courier.getId(),
                courier.getCourierAvailabilityStatus());
    }
}
