package com.yusufnazim.deliverydispatch.courier.dto;

import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.CourierVehicleType;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;
import java.time.Instant;

public record OperationalCourierResponse(
        Long id,
        String email,
        String displayName,
        String phoneNumber,
        CourierVehicleType vehicleType,
        CourierAvailabilityStatus availabilityStatus,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt,
        Instant updatedAt
) {

    public static OperationalCourierResponse from(User courier) {
        return new OperationalCourierResponse(
                courier.getId(),
                courier.getEmail(),
                courier.getCourierDisplayName(),
                courier.getCourierPhoneNumber(),
                courier.getCourierVehicleType(),
                courier.getCourierAvailabilityStatus(),
                courier.getCourierLatitude(),
                courier.getCourierLongitude(),
                courier.getCreatedAt(),
                courier.getUpdatedAt());
    }
}
