package com.yusufnazim.deliverydispatch.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    List<DeliveryOrder> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);
}
