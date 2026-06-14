package com.awbd.cinema.services.NotificationService;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.utils.RestPage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    @CacheEvict(value = "user_notifications", key = "#dto.userId()")
    public NotificationDTO createNotification(CreateNotificationDTO dto) {
        Order lastOrder = orderRepository.findFirstByUserIdOrderByCreatedAtDesc(dto.userId()).orElse(null);

        Notification notification = Notification.builder()
                .type(dto.type())
                .content(dto.content())
                .createdDate(LocalDateTime.now())
                .userId(dto.userId())
                .order(lastOrder)
                .build();

        return NotificationDTO.from(notificationRepository.save(notification));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_notifications")
    public RestPage<NotificationDTO> getMyNotifications(Long userId, Pageable pageable) {
        return new RestPage<>(notificationRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable)
                .map(NotificationDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "notification", key = "#id")
    public NotificationDTO getNotification(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found with id: " + id));
        return NotificationDTO.from(notification);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "notification", key = "#id"),
            @CacheEvict(value = "user_notifications", key = "#result.userId()")
    })
    public NotificationDTO markAsSent(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found with id: " + id));
        notification.setSentDate(LocalDateTime.now());
        return NotificationDTO.from(notificationRepository.save(notification));
    }
}
