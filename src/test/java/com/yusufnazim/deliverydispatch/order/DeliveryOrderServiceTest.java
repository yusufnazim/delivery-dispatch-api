package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.CustomerNotFoundException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryOrderServiceTest {

    @Mock
    private DeliveryOrderRepository deliveryOrderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeliveryOrderService deliveryOrderService;

    @Test
    void createOrderSavesPendingOrderForAuthenticatedCustomer() {
        User customer = new User("customer@example.com", "hashed-password", Role.CUSTOMER);
        CreateDeliveryOrderRequest request = validRequest();
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(deliveryOrderRepository.save(any(DeliveryOrder.class))).thenAnswer(invocation -> {
            DeliveryOrder order = invocation.getArgument(0);
            order.onCreate();
            return order;
        });

        DeliveryOrderResponse response = deliveryOrderService.createOrder(7L, request);

        ArgumentCaptor<DeliveryOrder> orderCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderRepository).save(orderCaptor.capture());
        DeliveryOrder savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getCustomer()).isSameAs(customer);
        assertThat(savedOrder.getCourier()).isNull();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getPickupAddress()).isEqualTo("Istiklal Cd. No:1, Beyoglu");
        assertThat(savedOrder.getPickupLatitude()).isEqualByComparingTo("41.036900");
        assertThat(savedOrder.getPickupLongitude()).isEqualByComparingTo("28.985000");
        assertThat(savedOrder.getDropoffAddress()).isEqualTo("Bagdat Cd. No:10, Kadikoy");
        assertThat(savedOrder.getDropoffLatitude()).isEqualByComparingTo("40.970000");
        assertThat(savedOrder.getDropoffLongitude()).isEqualByComparingTo("29.057000");

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.pickupAddress()).isEqualTo("Istiklal Cd. No:1, Beyoglu");
        assertThat(response.pickupLatitude()).isEqualByComparingTo("41.036900");
        assertThat(response.pickupLongitude()).isEqualByComparingTo("28.985000");
        assertThat(response.dropoffAddress()).isEqualTo("Bagdat Cd. No:10, Kadikoy");
        assertThat(response.dropoffLatitude()).isEqualByComparingTo("40.970000");
        assertThat(response.dropoffLongitude()).isEqualByComparingTo("29.057000");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void createOrderRejectsMissingCustomer() {
        CreateDeliveryOrderRequest request = validRequest();
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryOrderService.createOrder(404L, request))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Customer not found: 404");

        verify(deliveryOrderRepository, never()).save(any());
    }

    @Test
    void getCustomerOrderReturnsOwnedOrder() {
        DeliveryOrder order = order(11L, OrderStatus.PENDING, "Istiklal Cd. No:1, Beyoglu");
        when(deliveryOrderRepository.findByIdAndCustomerId(11L, 7L)).thenReturn(Optional.of(order));

        DeliveryOrderResponse response = deliveryOrderService.getCustomerOrder(7L, 11L);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.pickupAddress()).isEqualTo("Istiklal Cd. No:1, Beyoglu");
        assertThat(response.pickupLatitude()).isEqualByComparingTo("41.036900");
        assertThat(response.pickupLongitude()).isEqualByComparingTo("28.985000");
        assertThat(response.dropoffAddress()).isEqualTo("Bagdat Cd. No:10, Kadikoy");
        assertThat(response.dropoffLatitude()).isEqualByComparingTo("40.970000");
        assertThat(response.dropoffLongitude()).isEqualByComparingTo("29.057000");
    }

    @Test
    void getCustomerOrderRejectsMissingOrUnownedOrder() {
        when(deliveryOrderRepository.findByIdAndCustomerId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryOrderService.getCustomerOrder(7L, 99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: 99");
    }

    @Test
    void listCustomerOrdersReturnsOrdersFromRepositoryOrder() {
        DeliveryOrder newestOrder = order(12L, OrderStatus.PENDING, "Pickup B");
        DeliveryOrder olderOrder = order(11L, OrderStatus.PENDING, "Pickup A");
        when(deliveryOrderRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(7L))
                .thenReturn(List.of(newestOrder, olderOrder));

        List<DeliveryOrderResponse> responses = deliveryOrderService.listCustomerOrders(7L);

        assertThat(responses)
                .extracting(DeliveryOrderResponse::id)
                .containsExactly(12L, 11L);
        assertThat(responses)
                .extracting(DeliveryOrderResponse::pickupAddress)
                .containsExactly("Pickup B", "Pickup A");
    }

    private CreateDeliveryOrderRequest validRequest() {
        return new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }

    private DeliveryOrder order(Long id, OrderStatus status, String pickupAddress) {
        DeliveryOrder order = org.mockito.Mockito.mock(DeliveryOrder.class);
        when(order.getId()).thenReturn(id);
        when(order.getStatus()).thenReturn(status);
        when(order.getPickupAddress()).thenReturn(pickupAddress);
        when(order.getPickupLatitude()).thenReturn(new BigDecimal("41.036900"));
        when(order.getPickupLongitude()).thenReturn(new BigDecimal("28.985000"));
        when(order.getDropoffAddress()).thenReturn("Bagdat Cd. No:10, Kadikoy");
        when(order.getDropoffLatitude()).thenReturn(new BigDecimal("40.970000"));
        when(order.getDropoffLongitude()).thenReturn(new BigDecimal("29.057000"));
        when(order.getCreatedAt()).thenReturn(Instant.parse("2026-06-30T10:00:00Z"));
        when(order.getUpdatedAt()).thenReturn(Instant.parse("2026-06-30T10:05:00Z"));
        return order;
    }
}
