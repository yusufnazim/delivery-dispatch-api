package com.yusufnazim.deliverydispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringJUnitConfig(classes = DispatchServiceTransactionTest.TransactionTestConfig.class)
@TestExecutionListeners(
        listeners = DependencyInjectionTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class DispatchServiceTransactionTest {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private RepositoryStubs repositoryStubs;

    @Autowired
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        repositoryStubs.reset();
        transactionManager.clear();
    }

    @Test
    void assignNearestEligibleCourierRunsInsideWriteTransaction() {
        DeliveryOrder order = order();
        User courier = courierAt("courier@example.com", "41.037200", "28.985300");
        repositoryStubs.orderById = Optional.of(order);
        repositoryStubs.eligibleCouriers = List.of(courier);

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
        repositoryStubs.orderById = Optional.of(order);
        repositoryStubs.courierByIdAndRole = Optional.of(courier);

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
        RepositoryStubs repositoryStubs() {
            return new RepositoryStubs();
        }

        @Bean
        DeliveryOrderRepository deliveryOrderRepository(RepositoryStubs repositoryStubs) {
            return repositoryProxy(DeliveryOrderRepository.class, repositoryStubs::invokeDeliveryOrderRepository);
        }

        @Bean
        UserRepository userRepository(RepositoryStubs repositoryStubs) {
            return repositoryProxy(UserRepository.class, repositoryStubs::invokeUserRepository);
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    static class RepositoryStubs {

        private Optional<DeliveryOrder> orderById = Optional.empty();
        private Optional<User> courierByIdAndRole = Optional.empty();
        private List<User> eligibleCouriers = List.of();

        void reset() {
            orderById = Optional.empty();
            courierByIdAndRole = Optional.empty();
            eligibleCouriers = List.of();
        }

        Object invokeDeliveryOrderRepository(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findById" -> orderById;
                case "existsByCourierIdAndStatusIn" -> false;
                case "flush" -> null;
                default -> invokeObjectMethod(proxy, method, args);
            };
        }

        Object invokeUserRepository(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findEligibleCouriersForDispatch" -> eligibleCouriers;
                case "findByIdAndRole" -> courierByIdAndRole;
                default -> invokeObjectMethod(proxy, method, args);
            };
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + "Stub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            throw new UnsupportedOperationException(method.getName());
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

    @SuppressWarnings("unchecked")
    private static <T> T repositoryProxy(Class<T> repositoryType, InvocationHandler invocationHandler) {
        return (T) Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[] {repositoryType},
                invocationHandler);
    }
}
