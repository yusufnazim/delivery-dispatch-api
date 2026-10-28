package com.yusufnazim.deliverydispatch.courier;

import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.dto.OperationalCourierResponse;
import com.yusufnazim.deliverydispatch.courier.exception.CourierAvailabilityChangeNotAllowedException;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.courier.exception.CourierUpdateConflictException;
import com.yusufnazim.deliverydispatch.courier.exception.InvalidCourierAvailabilityStatusException;
import com.yusufnazim.deliverydispatch.dto.PageResponse;
import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourierService {

    private static final List<OrderStatus> ACTIVE_DELIVERY_STATUSES = List.of(
            OrderStatus.ASSIGNED, OrderStatus.PICKED_UP);

    private final UserRepository userRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryTimelineService deliveryTimelineService;

    @Transactional(readOnly = true)
    public Optional<DeliveryOrderResponse> getCurrentOrder(Long courierId) {
        return deliveryOrderRepository.findByCourierIdAndStatusIn(courierId, ACTIVE_DELIVERY_STATUSES)
                .map(DeliveryOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationalCourierResponse> listOperationalCouriers(
            CourierAvailabilityStatus availability, int page, int size) {
        PageRequest request = PageRequest.of(page, size);
        Page<User> couriers = availability == null
                ? userRepository.findByRoleOrderByIdAsc(Role.COURIER, request)
                : userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(Role.COURIER, availability, request);
        return PageResponse.from(couriers.map(OperationalCourierResponse::from));
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

        if (courier.getCourierAvailabilityStatus() == CourierAvailabilityStatus.ON_DELIVERY
                || deliveryOrderRepository.existsByCourierIdAndStatusIn(courierId, ACTIVE_DELIVERY_STATUSES)) {
            throw new CourierAvailabilityChangeNotAllowedException(courierId);
        }

        courier.updateCourierAvailabilityStatus(availabilityStatus);
        flushCourierChanges(courierId);

        return CourierAvailabilityResponse.from(courier);
    }

    @Transactional
    public CourierLocationResponse updateLocation(Long courierId, BigDecimal latitude, BigDecimal longitude) {
        User courier = userRepository.findByIdAndRole(courierId, Role.COURIER)
                .orElseThrow(() -> new CourierNotFoundException(courierId));

        courier.updateCourierLocation(latitude, longitude);
        flushCourierChanges(courierId);

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
        flushCourierChanges(courierId);
        deliveryTimelineService.recordOrderDelivered(order);

        return DeliveryOrderResponse.from(order);
    }

    private void flushCourierChanges(Long courierId) {
        try {
            // Flush the whole persistence context, including completion, before recording its event.
            userRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new CourierUpdateConflictException(courierId, exception);
        }
    }
}
