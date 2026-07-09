package com.yusufnazim.deliverydispatch.timeline;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.timeline.dto.DeliveryEventResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryTimelineService {

    private final DeliveryEventRepository deliveryEventRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;

    @Transactional(readOnly = true)
    public List<DeliveryEventResponse> getCustomerOrderTimeline(Long customerId, Long orderId) {
        deliveryOrderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return deliveryEventRepository.findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(orderId).stream()
                .map(DeliveryEventResponse::from)
                .toList();
    }

    public void recordOrderCreated(DeliveryOrder order) {
        record(order, DeliveryEventType.ORDER_CREATED, "Order created");
    }

    public void recordCourierAssigned(DeliveryOrder order) {
        record(order, DeliveryEventType.COURIER_ASSIGNED, "Courier assigned");
    }

    public void recordOrderPickedUp(DeliveryOrder order) {
        record(order, DeliveryEventType.ORDER_PICKED_UP, "Order picked up");
    }

    public void recordOrderDelivered(DeliveryOrder order) {
        record(order, DeliveryEventType.ORDER_DELIVERED, "Order delivered");
    }

    public void recordOrderCancelled(DeliveryOrder order) {
        record(order, DeliveryEventType.ORDER_CANCELLED, "Order cancelled");
    }

    private void record(DeliveryOrder order, DeliveryEventType eventType, String description) {
        deliveryEventRepository.save(new DeliveryEvent(order, eventType, description));
    }
}
