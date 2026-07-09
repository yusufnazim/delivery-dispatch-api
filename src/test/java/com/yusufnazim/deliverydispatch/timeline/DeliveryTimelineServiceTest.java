package com.yusufnazim.deliverydispatch.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.timeline.dto.DeliveryEventResponse;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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

    @Mock
    private DeliveryOrderRepository deliveryOrderRepository;

    @InjectMocks
    private DeliveryTimelineService deliveryTimelineService;

    @Test
    void getCustomerOrderTimelineReturnsOwnedOrderEvents() {
        DeliveryOrder order = order();
        DeliveryEvent created = event(order, DeliveryEventType.ORDER_CREATED, "Order created");
        DeliveryEvent pickedUp = event(order, DeliveryEventType.ORDER_PICKED_UP, "Order picked up");
        when(deliveryOrderRepository.findByIdAndCustomerId(11L, 7L)).thenReturn(Optional.of(order));
        when(deliveryEventRepository.findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(11L))
                .thenReturn(List.of(created, pickedUp));

        List<DeliveryEventResponse> timeline = deliveryTimelineService.getCustomerOrderTimeline(7L, 11L);

        assertThat(timeline)
                .extracting(DeliveryEventResponse::type)
                .containsExactly(DeliveryEventType.ORDER_CREATED, DeliveryEventType.ORDER_PICKED_UP);
        assertThat(timeline)
                .extracting(DeliveryEventResponse::description)
                .containsExactly("Order created", "Order picked up");
    }

    @Test
    void getCustomerOrderTimelineRejectsMissingOrUnownedOrder() {
        when(deliveryOrderRepository.findByIdAndCustomerId(404L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryTimelineService.getCustomerOrderTimeline(7L, 404L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: 404");

        verifyNoInteractions(deliveryEventRepository);
    }

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

    private DeliveryEvent event(DeliveryOrder order, DeliveryEventType eventType, String description) {
        DeliveryEvent event = new DeliveryEvent(order, eventType, description);
        event.onCreate();
        return event;
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
