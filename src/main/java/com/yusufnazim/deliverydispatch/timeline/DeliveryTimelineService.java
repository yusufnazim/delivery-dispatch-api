package com.yusufnazim.deliverydispatch.timeline;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryTimelineService {

    private final DeliveryEventRepository deliveryEventRepository;

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
