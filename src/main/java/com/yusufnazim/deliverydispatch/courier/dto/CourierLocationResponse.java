package com.yusufnazim.deliverydispatch.courier.dto;

import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;

public record CourierLocationResponse(
        Long courierId,
        BigDecimal latitude,
        BigDecimal longitude
) {

    public static CourierLocationResponse from(User courier) {
        return new CourierLocationResponse(
                courier.getId(),
                courier.getCourierLatitude(),
                courier.getCourierLongitude());
    }
}
