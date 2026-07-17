package com.yusufnazim.deliverydispatch.user;

import com.yusufnazim.deliverydispatch.order.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndRole(Long id, Role role);

    List<User> findByRoleOrderByIdAsc(Role role);

    @Query("""
            select user
            from User user
            where user.role = :role
              and user.courierAvailabilityStatus = :availabilityStatus
              and user.courierLatitude is not null
              and user.courierLongitude is not null
              and not exists (
                  select deliveryOrder.id
                  from DeliveryOrder deliveryOrder
                  where deliveryOrder.courier = user
                    and deliveryOrder.status in :activeStatuses
              )
            order by user.id asc
            """)
    List<User> findEligibleCouriersForDispatch(
            Role role,
            CourierAvailabilityStatus availabilityStatus,
            Collection<OrderStatus> activeStatuses);

    boolean existsByEmail(String email);
}
