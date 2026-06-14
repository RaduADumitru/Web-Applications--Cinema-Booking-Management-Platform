package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.services.NotificationService.NotificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks private NotificationServiceImpl notificationService;

    @Nested
    @DisplayName("Tests for createNotification")
    class CreateNotificationTests {

        @Test
        void createNotification_WithExistingOrder_ReturnsNotificationDTO() {
            Long userId = 1L;
            Long orderId = 100L;
            CreateNotificationDTO dto = new CreateNotificationDTO(NotificationType.TICKET_BOUGHT, "Your ticket is confirmed!", userId);

            Order lastOrder = Order.builder().id(orderId).price(BigDecimal.TEN).userId(userId).build();

            Notification savedNotification = Notification.builder()
                    .id(50L).type(dto.type()).content(dto.content())
                    .createdDate(LocalDateTime.now()).userId(userId).order(lastOrder).build();

            when(orderRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(lastOrder));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            NotificationDTO result = notificationService.createNotification(dto);

            assertNotNull(result);
            assertEquals(50L, result.id());
            assertEquals(userId, result.userId());
            assertNotNull(result.order());
            assertEquals(orderId, result.order().id());

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification captured = captor.getValue();
            assertEquals(dto.type(), captured.getType());
            assertEquals(dto.content(), captured.getContent());
            assertEquals(userId, captured.getUserId());
            assertEquals(lastOrder, captured.getOrder());
            assertNotNull(captured.getCreatedDate());
        }

        @Test
        void createNotification_WithNoOrders_ReturnsNotificationDTO_WithNullOrder() {
            Long userId = 1L;
            CreateNotificationDTO dto = new CreateNotificationDTO(NotificationType.MOVIE_REMINDER, "Don't miss your show!", userId);

            Notification savedNotification = Notification.builder()
                    .id(51L).type(dto.type()).content(dto.content())
                    .createdDate(LocalDateTime.now()).userId(userId).order(null).build();

            when(orderRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            NotificationDTO result = notificationService.createNotification(dto);

            assertNotNull(result);
            assertEquals(51L, result.id());
            assertNull(result.order());

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertNull(captor.getValue().getOrder());
            assertEquals(userId, captor.getValue().getUserId());
        }
    }

    @Nested
    @DisplayName("Tests for getMyNotifications")
    class GetMyNotificationsTests {
        @Test
        void getMyNotifications_ReturnsPagedNotifications() {
            Long userId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            Notification n1 = Notification.builder().id(10L).type(NotificationType.SUCCESSFUL_PAYMENT).content("Paid!").userId(userId).build();
            Notification n2 = Notification.builder().id(11L).type(NotificationType.MOVIE_REMINDER).content("Reminder!").userId(userId).build();
            Page<Notification> page = new PageImpl<>(List.of(n1, n2), pageable, 2);

            when(notificationRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable)).thenReturn(page);

            Page<NotificationDTO> result = notificationService.getMyNotifications(userId, pageable);

            assertNotNull(result);
            assertEquals(2, result.getTotalElements());
            assertEquals(10L, result.getContent().getFirst().id());
            verify(notificationRepository, times(1)).findByUserIdOrderByCreatedDateDesc(userId, pageable);
        }
    }

    @Nested
    @DisplayName("Tests for getNotification")
    class GetNotificationTests {
        @Test
        void getNotification_ExistingId_ReturnsNotificationDTO() {
            Long notificationId = 1L;
            Notification notification = Notification.builder()
                    .id(notificationId).type(NotificationType.REGISTERED_FROM_ANOTHER_DEVICE)
                    .content("New login detected").userId(42L).build();

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            NotificationDTO result = notificationService.getNotification(notificationId);

            assertNotNull(result);
            assertEquals(notificationId, result.id());
            assertEquals("New login detected", result.content());
            assertEquals(42L, result.userId());
        }

        @Test
        void getNotification_NonExistingId_ThrowsNotFoundException() {
            Long notificationId = 999L;
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());
            NotFoundException ex = assertThrows(NotFoundException.class, () -> notificationService.getNotification(notificationId));
            assertEquals("Notification not found with id: " + notificationId, ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Tests for markAsSent")
    class MarkAsSentTests {
        @Test
        void markAsSent_ExistingId_UpdatesSentDateAndReturnsDTO() {
            Long notificationId = 1L;
            Notification notification = Notification.builder()
                    .id(notificationId).type(NotificationType.EMAIL_VERIFICATION)
                    .content("Verify your account").userId(42L).sentDate(null).build();

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

            NotificationDTO result = notificationService.markAsSent(notificationId);

            assertNotNull(result);
            assertNotNull(result.sentDate());
            verify(notificationRepository, times(1)).save(notification);
        }

        @Test
        void markAsSent_NonExistingId_ThrowsNotFoundException() {
            Long notificationId = 999L;
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());
            NotFoundException ex = assertThrows(NotFoundException.class, () -> notificationService.markAsSent(notificationId));
            assertEquals("Notification not found with id: " + notificationId, ex.getMessage());
            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }
}
