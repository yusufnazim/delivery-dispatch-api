package com.yusufnazim.deliverydispatch.timeline;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {

    List<DeliveryEvent> findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(Long deliveryOrderId);
}
