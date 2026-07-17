package com.yusufnazim.deliverydispatch.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    Optional<DeliveryOrder> findByIdAndCustomerId(Long id, Long customerId);

    Optional<DeliveryOrder> findByIdAndCourierId(Long id, Long courierId);

    List<DeliveryOrder> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "courier"})
    List<DeliveryOrder> findAllByOrderByCreatedAtDescIdDesc();

    boolean existsByCourierIdAndStatusIn(Long courierId, Collection<OrderStatus> statuses);
}
