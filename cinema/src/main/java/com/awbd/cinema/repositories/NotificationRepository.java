package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedDateDesc(Long userId);
}
