package com.yusufnazim.deliverydispatch.demo;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.CourierVehicleType;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "DemoPass123!";

    private final UserRepository userRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryTimelineService deliveryTimelineService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        seedUser("admin@delivery.local", Role.ADMIN);
        seedUser("dispatcher@delivery.local", Role.DISPATCHER);
        User customer = seedUser("customer@delivery.local", Role.CUSTOMER);
        User firstCourier = seedCourier(
                "courier.one@delivery.local",
                "Ayse Courier",
                "+905550000001",
                CourierVehicleType.MOTORBIKE,
                "41.026500",
                "28.975000");
        seedCourier(
                "courier.two@delivery.local",
                "Mehmet Courier",
                "+905550000002",
                CourierVehicleType.BICYCLE,
                "40.991500",
                "29.025000");

        seedOrders(customer, firstCourier);
        log.info("Local demo data is ready for customer@delivery.local");
    }

    private User seedUser(String email, Role role) {
        return userRepository.findByEmail(email)
                .map(user -> requireRole(user, role))
                .orElseGet(() -> userRepository.save(new User(
                        email,
                        passwordEncoder.encode(DEMO_PASSWORD),
                        role)));
    }

    private User seedCourier(
            String email,
            String displayName,
            String phoneNumber,
            CourierVehicleType vehicleType,
            String latitude,
            String longitude) {
        return userRepository.findByEmail(email)
                .map(user -> requireRole(user, Role.COURIER))
                .orElseGet(() -> {
                    User courier = new User(
                            email,
                            passwordEncoder.encode(DEMO_PASSWORD),
                            Role.COURIER);
                    courier.updateCourierProfile(displayName, phoneNumber, vehicleType);
                    courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
                    courier.updateCourierLocation(new BigDecimal(latitude), new BigDecimal(longitude));
                    return userRepository.save(courier);
                });
    }

    private User requireRole(User user, Role expectedRole) {
        if (user.getRole() != expectedRole) {
            throw new IllegalStateException(
                    "Demo user %s must have role %s".formatted(user.getEmail(), expectedRole));
        }
        return user;
    }

    private void seedOrders(User customer, User courier) {
        if (!deliveryOrderRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId()).isEmpty()) {
            return;
        }

        DeliveryOrder pendingOrder = deliveryOrderRepository.save(new DeliveryOrder(
                customer,
                "Istiklal Cd., Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd., Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000")));
        deliveryTimelineService.recordOrderCreated(pendingOrder);

        DeliveryOrder completedOrder = deliveryOrderRepository.save(new DeliveryOrder(
                customer,
                "Galata Tower, Beyoglu",
                new BigDecimal("41.025600"),
                new BigDecimal("28.974100"),
                "Kadikoy Pier",
                new BigDecimal("40.990900"),
                new BigDecimal("29.023300")));
        deliveryTimelineService.recordOrderCreated(completedOrder);

        completedOrder.assignCourier(courier);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.ON_DELIVERY);
        deliveryTimelineService.recordCourierAssigned(completedOrder);

        completedOrder.markPickedUp();
        deliveryTimelineService.recordOrderPickedUp(completedOrder);

        completedOrder.markDelivered();
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        deliveryTimelineService.recordOrderDelivered(completedOrder);
    }
}
