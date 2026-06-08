package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.repositories.UserRepository;
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

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Nested
    @DisplayName("Tests for createNotification")
    class CreateNotificationTests {

        @Test
        @DisplayName("Should create notification successfully when user and latest order exist")
        void createNotification_WithExistingOrder_ReturnsNotificationDTO() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;
            CreateNotificationDTO dto = new CreateNotificationDTO(NotificationType.TICKET_BOUGHT, "Your ticket is confirmed!", userId);

            User user = User.builder().id(userId).username("john_doe").build();
            Order lastOrder = Order.builder().id(orderId).price(BigDecimal.TEN).user(user).build();

            Notification savedNotification = Notification.builder()
                    .id(50L)
                    .type(dto.type())
                    .content(dto.content())
                    .createdDate(LocalDateTime.now())
                    .user(user)
                    .order(lastOrder)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(orderRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(lastOrder));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // Act
            NotificationDTO result = notificationService.createNotification(dto);

            // Assert
            assertNotNull(result);
            assertEquals(50L, result.id());
            assertEquals(dto.content(), result.content());
            assertEquals(userId, result.userId());
            assertNotNull(result.order());
            assertEquals(orderId, result.order().id());

            // Capture and verify the Notification instance passed to the repository
            ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(notificationCaptor.capture());
            Notification captured = notificationCaptor.getValue();

            assertEquals(dto.type(), captured.getType());
            assertEquals(dto.content(), captured.getContent());
            assertEquals(user, captured.getUser());
            assertEquals(lastOrder, captured.getOrder());
            assertNotNull(captured.getCreatedDate());
        }

        @Test
        @DisplayName("Should create notification successfully when user exists but has no orders")
        void createNotification_WithNoOrders_ReturnsNotificationDTO_WithNullOrder() {
            // Arrange
            Long userId = 1L;
            CreateNotificationDTO dto = new CreateNotificationDTO(NotificationType.MOVIE_REMINDER, "Don't miss your show!", userId);

            User user = User.builder().id(userId).username("jane_doe").build();
            Notification savedNotification = Notification.builder()
                    .id(51L)
                    .type(dto.type())
                    .content(dto.content())
                    .createdDate(LocalDateTime.now())
                    .user(user)
                    .order(null) // explicitly null
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(orderRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // Act
            NotificationDTO result = notificationService.createNotification(dto);

            // Assert
            assertNotNull(result);
            assertEquals(51L, result.id());
            assertNull(result.order());

            ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(notificationCaptor.capture());
            assertNull(notificationCaptor.getValue().getOrder());
        }

        @Test
        @DisplayName("Should throw NotFoundException when user does not exist")
        void createNotification_UserNotFound_ThrowsNotFoundException() {
            // Arrange
            Long userId = 999L;
            CreateNotificationDTO dto = new CreateNotificationDTO(NotificationType.EMAIL_VERIFICATION, "Verify email", userId);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    notificationService.createNotification(dto)
            );

            assertEquals("User not found with id: " + userId, exception.getMessage());
            verifyNoInteractions(orderRepository, notificationRepository);
        }
    }

    @Nested
    @DisplayName("Tests for getMyNotifications")
    class GetMyNotificationsTests {

        @Test
        @DisplayName("Should return a paginated list of notification DTOs")
        void getMyNotifications_ReturnsPagedNotifications() {
            // Arrange
            Long userId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            User user = User.builder().id(userId).build();

            Notification n1 = Notification.builder().id(10L).type(NotificationType.SUCCESSFUL_PAYMENT).content("Paid!").user(user).build();
            Notification n2 = Notification.builder().id(11L).type(NotificationType.MOVIE_REMINDER).content("Reminder!").user(user).build();
            Page<Notification> notificationPage = new PageImpl<>(List.of(n1, n2), pageable, 2);

            when(notificationRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable)).thenReturn(notificationPage);

            // Act
            Page<NotificationDTO> result = notificationService.getMyNotifications(userId, pageable);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getTotalElements());
            assertEquals(10L, result.getContent().getFirst().id());
            assertEquals(11L, result.getContent().get(1).id());
            verify(notificationRepository, times(1)).findByUserIdOrderByCreatedDateDesc(userId, pageable);
        }
    }

    @Nested
    @DisplayName("Tests for getNotification")
    class GetNotificationTests {

        @Test
        @DisplayName("Should return NotificationDTO when given a valid notification ID")
        void getNotification_ExistingId_ReturnsNotificationDTO() {
            // Arrange
            Long notificationId = 1L;
            User user = User.builder().id(42L).build();
            Notification notification = Notification.builder()
                    .id(notificationId)
                    .type(NotificationType.REGISTERED_FROM_ANOTHER_DEVICE)
                    .content("New login detected")
                    .user(user)
                    .build();

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            // Act
            NotificationDTO result = notificationService.getNotification(notificationId);

            // Assert
            assertNotNull(result);
            assertEquals(notificationId, result.id());
            assertEquals("New login detected", result.content());
            assertEquals(42L, result.userId());
        }

        @Test
        @DisplayName("Should throw NotFoundException when notification ID does not exist")
        void getNotification_NonExistingId_ThrowsNotFoundException() {
            // Arrange
            Long notificationId = 999L;
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    notificationService.getNotification(notificationId)
            );

            assertEquals("Notification not found with id: " + notificationId, exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Tests for markAsSent")
    class MarkAsSentTests {

        @Test
        @DisplayName("Should update sent date and save notification when id is found")
        void markAsSent_ExistingId_UpdatesSentDateAndReturnsDTO() {
            // Arrange
            Long notificationId = 1L;
            User user = User.builder().id(42L).build();
            Notification notification = Notification.builder()
                    .id(notificationId)
                    .type(NotificationType.EMAIL_VERIFICATION)
                    .content("Verify your account")
                    .user(user)
                    .sentDate(null) // Starts as null
                    .build();

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
            // Return the object itself after saving
            when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            NotificationDTO result = notificationService.markAsSent(notificationId);

            // Assert
            assertNotNull(result);
            assertNotNull(result.sentDate(), "Sent date should be updated to current time");
            verify(notificationRepository, times(1)).save(notification);
        }

        @Test
        @DisplayName("Should throw NotFoundException when marking a non-existent notification as sent")
        void markAsSent_NonExistingId_ThrowsNotFoundException() {
            // Arrange
            Long notificationId = 999L;
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    notificationService.markAsSent(notificationId)
            );

            assertEquals("Notification not found with id: " + notificationId, exception.getMessage());
            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }
}