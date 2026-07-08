package com.yusufnazim.deliverydispatch;

import com.yusufnazim.deliverydispatch.user.UserRepository;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventRepository;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TestRepositoryConfig {

    @Bean
    @ConditionalOnMissingBean(UserRepository.class)
    UserRepository userRepository() {
        return Mockito.mock(UserRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean(DeliveryOrderRepository.class)
    DeliveryOrderRepository deliveryOrderRepository() {
        return Mockito.mock(DeliveryOrderRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean(DeliveryEventRepository.class)
    DeliveryEventRepository deliveryEventRepository() {
        return Mockito.mock(DeliveryEventRepository.class);
    }
}
