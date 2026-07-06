package com.yusufnazim.deliverydispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringJUnitConfig(classes = DispatchServiceTransactionTest.TransactionTestConfig.class)
class DispatchServiceTransactionTest {

    private static final List<OrderStatus> ACTIVE_DELIVERY_STATUSES = List.of(
            OrderStatus.ASSIGNED,
            OrderStatus.PICKED_UP);

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        reset(deliveryOrderRepository, userRepository);
        transactionManager.clear();
    }

    @Test
    void assignNearestEligibleCourierRunsInsideWriteTransaction() {
        DeliveryOrder order = order();
        User courier = courierAt("courier@example.com", "41.037200", "28.985300");
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findEligibleCouriersForDispatch(
                Role.COURIER,
                CourierAvailabilityStatus.AVAILABLE,
                ACTIVE_DELIVERY_STATUSES))
                .thenReturn(List.of(courier));

        dispatchService.assignNearestEligibleCourier(100L);

        TransactionBoundary boundary = onlyTransactionBoundary();
        assertThat(AopUtils.isAopProxy(dispatchService)).isTrue();
        assertThat(boundary.name()).endsWith("DispatchService.assignNearestEligibleCourier");
        assertThat(boundary.readOnly()).isFalse();
        assertThat(transactionManager.commits()).isEqualTo(1);
        assertThat(transactionManager.rollbacks()).isZero();
    }

    @Test
    void assignCourierToOrderRunsInsideWriteTransaction() {
        DeliveryOrder order = order();
        User courier = courierAt("courier@example.com", "41.037200", "28.985300");
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.of(courier));
        when(deliveryOrderRepository.existsByCourierIdAndStatusIn(7L, ACTIVE_DELIVERY_STATUSES)).thenReturn(false);

        dispatchService.assignCourierToOrder(100L, 7L);

        TransactionBoundary boundary = onlyTransactionBoundary();
        assertThat(AopUtils.isAopProxy(dispatchService)).isTrue();
        assertThat(boundary.name()).endsWith("DispatchService.assignCourierToOrder");
        assertThat(boundary.readOnly()).isFalse();
        assertThat(transactionManager.commits()).isEqualTo(1);
        assertThat(transactionManager.rollbacks()).isZero();
    }

    private TransactionBoundary onlyTransactionBoundary() {
        assertThat(transactionManager.boundaries()).hasSize(1);
        return transactionManager.boundaries().getFirst();
    }

    private static User courierAt(String email, String latitude, String longitude) {
        User courier = new User(email, "hashed-password", Role.COURIER);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        courier.updateCourierLocation(new BigDecimal(latitude), new BigDecimal(longitude));
        return courier;
    }

    private static DeliveryOrder order() {
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

    @Configuration
    @EnableTransactionManagement
    static class TransactionTestConfig {

        @Bean
        DispatchService dispatchService(
                DeliveryOrderRepository deliveryOrderRepository,
                UserRepository userRepository,
                HaversineDistanceCalculator distanceCalculator) {
            return new DispatchService(deliveryOrderRepository, userRepository, distanceCalculator);
        }

        @Bean
        HaversineDistanceCalculator distanceCalculator() {
            return new HaversineDistanceCalculator();
        }

        @Bean
        DeliveryOrderRepository deliveryOrderRepository() {
            return mock(DeliveryOrderRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private final List<TransactionBoundary> boundaries = new ArrayList<>();
        private int commits;
        private int rollbacks;

        List<TransactionBoundary> boundaries() {
            return List.copyOf(boundaries);
        }

        int commits() {
            return commits;
        }

        int rollbacks() {
            return rollbacks;
        }

        void clear() {
            boundaries.clear();
            commits = 0;
            rollbacks = 0;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            boundaries.add(new TransactionBoundary(definition.getName(), definition.isReadOnly()));
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    private record TransactionBoundary(String name, boolean readOnly) {
    }
}
