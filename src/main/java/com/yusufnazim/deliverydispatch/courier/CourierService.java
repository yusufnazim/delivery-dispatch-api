package com.yusufnazim.deliverydispatch.courier;

import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.dto.OperationalCourierResponse;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.courier.exception.InvalidCourierAvailabilityStatusException;
import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final UserRepository userRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryTimelineService deliveryTimelineService;

    @Transactional(readOnly = true)
    public List<OperationalCourierResponse> listOperationalCouriers() {
        return userRepository.findByRoleOrderByIdAsc(Role.COURIER).stream()
                .map(OperationalCourierResponse::from)
                .toList();
    }

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

    @Transactional
    public DeliveryOrderResponse pickupOrder(Long courierId, Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findByIdAndCourierId(orderId, courierId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.markPickedUp();
        deliveryTimelineService.recordOrderPickedUp(order);

        return DeliveryOrderResponse.from(order);
    }

    @Transactional
    public DeliveryOrderResponse deliverOrder(Long courierId, Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findByIdAndCourierId(orderId, courierId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.markDelivered();
        order.getCourier().updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        deliveryTimelineService.recordOrderDelivered(order);

        return DeliveryOrderResponse.from(order);
    }
}
