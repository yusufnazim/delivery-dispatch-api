package com.yusufnazim.deliverydispatch.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndRole(Long id, Role role);

    @Query("""
            select user
            from User user
            where user.role = :role
              and user.courierAvailabilityStatus = :availabilityStatus
              and user.courierLatitude is not null
              and user.courierLongitude is not null
            order by user.id asc
            """)
    List<User> findEligibleCouriersForDispatch(Role role, CourierAvailabilityStatus availabilityStatus);

    boolean existsByEmail(String email);
}
