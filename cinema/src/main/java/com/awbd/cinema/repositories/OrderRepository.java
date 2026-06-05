package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Order;
import com.awbd.cinema.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Page<Order> findByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses, Pageable pageable);

    Optional<Order> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = "SELECT * FROM orders WHERE order_id = :id", nativeQuery = true)
    Optional<Order> findByIdIncludingDeleted(@Param("id") Long id);
}
