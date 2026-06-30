package com.yusufnazim.deliverydispatch.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    Optional<DeliveryOrder> findByIdAndCustomerId(Long id, Long customerId);

    List<DeliveryOrder> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);
}
