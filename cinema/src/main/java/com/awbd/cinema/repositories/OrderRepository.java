package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Order;
import com.awbd.cinema.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByUserId(Long userId);
    List<Order> findByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

    Optional<Order> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = "SELECT * FROM orders WHERE order_id = :id", nativeQuery = true)
    Optional<Order> findByIdIncludingDeleted(@Param("id") Long id);
}
