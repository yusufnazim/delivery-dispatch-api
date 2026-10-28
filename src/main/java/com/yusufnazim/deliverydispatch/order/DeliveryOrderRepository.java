package com.yusufnazim.deliverydispatch.order;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    Optional<DeliveryOrder> findByIdAndCustomerId(Long id, Long customerId);

    Optional<DeliveryOrder> findByIdAndCourierId(Long id, Long courierId);

    Optional<DeliveryOrder> findByCourierIdAndStatusIn(Long courierId, Collection<OrderStatus> statuses);

    Page<DeliveryOrder> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);

    boolean existsByCustomerId(Long customerId);

    @EntityGraph(attributePaths = {"customer", "courier"})
    Page<DeliveryOrder> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "courier"})
    Page<DeliveryOrder> findByStatusOrderByCreatedAtDescIdDesc(OrderStatus status, Pageable pageable);

    boolean existsByCourierIdAndStatusIn(Long courierId, Collection<OrderStatus> statuses);
}
