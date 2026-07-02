package com.yusufnazim.deliverydispatch.courier;

import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.courier.exception.InvalidCourierAvailabilityStatusException;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final UserRepository userRepository;

    @Transactional
    public CourierAvailabilityResponse updateAvailability(
            Long courierId,
            CourierAvailabilityStatus availabilityStatus) {
        if (availabilityStatus == CourierAvailabilityStatus.ON_DELIVERY) {
            throw new InvalidCourierAvailabilityStatusException(availabilityStatus);
        }

        User courier = userRepository.findByIdAndRole(courierId, Role.COURIER)
                .orElseThrow(() -> new CourierNotFoundException(courierId));

        courier.updateCourierAvailabilityStatus(availabilityStatus);

        return CourierAvailabilityResponse.from(courier);
    }

    @Transactional
    public CourierLocationResponse updateLocation(Long courierId, BigDecimal latitude, BigDecimal longitude) {
        User courier = userRepository.findByIdAndRole(courierId, Role.COURIER)
                .orElseThrow(() -> new CourierNotFoundException(courierId));

        courier.updateCourierLocation(latitude, longitude);

        return CourierLocationResponse.from(courier);
    }
}
