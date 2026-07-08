package com.yusufnazim.deliverydispatch.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryTimelineServiceTest {

    @Mock
    private DeliveryEventRepository deliveryEventRepository;

    @InjectMocks
    private DeliveryTimelineService deliveryTimelineService;

    @Test
    void recordsDeliveryWorkflowEvents() {
        DeliveryOrder order = order();

        deliveryTimelineService.recordOrderCreated(order);
        deliveryTimelineService.recordCourierAssigned(order);
        deliveryTimelineService.recordOrderPickedUp(order);
        deliveryTimelineService.recordOrderDelivered(order);
        deliveryTimelineService.recordOrderCancelled(order);

        ArgumentCaptor<DeliveryEvent> eventCaptor = ArgumentCaptor.forClass(DeliveryEvent.class);
        verify(deliveryEventRepository, times(5)).save(eventCaptor.capture());
        List<DeliveryEvent> events = eventCaptor.getAllValues();

        assertThat(events).allSatisfy(event -> assertThat(event.getDeliveryOrder()).isSameAs(order));
        assertThat(events)
                .extracting(DeliveryEvent::getEventType)
                .containsExactly(
                        DeliveryEventType.ORDER_CREATED,
                        DeliveryEventType.COURIER_ASSIGNED,
                        DeliveryEventType.ORDER_PICKED_UP,
                        DeliveryEventType.ORDER_DELIVERED,
                        DeliveryEventType.ORDER_CANCELLED);
        assertThat(events)
                .extracting(DeliveryEvent::getDescription)
                .containsExactly(
                        "Order created",
                        "Courier assigned",
                        "Order picked up",
                        "Order delivered",
                        "Order cancelled");
    }

    private DeliveryOrder order() {
        User customer = new User("customer@example.com", "hashed-password", Role.CUSTOMER);
        return new DeliveryOrder(
                customer,
                "Pickup",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Dropoff",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }
}
