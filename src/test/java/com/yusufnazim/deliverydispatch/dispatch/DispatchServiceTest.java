package com.yusufnazim.deliverydispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DispatchService dispatchService;

    @Test
    void findEligibleCouriersLooksUpAvailableCouriersWithKnownLocations() {
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);
        when(userRepository.findEligibleCouriersForDispatch(Role.COURIER, CourierAvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(courier));

        List<User> eligibleCouriers = dispatchService.findEligibleCouriers();

        assertThat(eligibleCouriers).containsExactly(courier);
    }
}
