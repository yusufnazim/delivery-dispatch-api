package com.yusufnazim.deliverydispatch.dispatch;

import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DispatchService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<User> findEligibleCouriers() {
        return userRepository.findEligibleCouriersForDispatch(Role.COURIER, CourierAvailabilityStatus.AVAILABLE);
    }
}
