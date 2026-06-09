package com.awbd.cinema.schedulers;

import com.awbd.cinema.entities.*;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock private ScreenSessionRepository screenSessionRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private OrderRepository orderRepository; // Injected due to RequiredArgsConstructor

    @InjectMocks
    private NotificationScheduler notificationScheduler;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        tomorrow = LocalDate.now().plusDays(1);
    }

    @Test
    void sendMovieReminders_ShouldDoNothing_WhenNoSessionsExistTomorrow() {
        // Branch 1: Sessions list is empty
        when(screenSessionRepository.findByDate(tomorrow)).thenReturn(Collections.emptyList());

        notificationScheduler.sendMovieReminders();

        verify(ticketRepository, never()).findByScreenSessionIdAndOrderIsNotNull(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendMovieReminders_ShouldDoNothing_WhenSessionsHaveNoBookedTickets() {
        // Branch 2: Sessions exist, but Ticket list is empty
        ScreenSession session = ScreenSession.builder().id(10L).date(tomorrow).build();
        when(screenSessionRepository.findByDate(tomorrow)).thenReturn(List.of(session));
        when(ticketRepository.findByScreenSessionIdAndOrderIsNotNull(10L)).thenReturn(Collections.emptyList());

        notificationScheduler.sendMovieReminders();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendMovieReminders_ShouldSendSingleNotificationPerUserPerSession_WhenDuplicateTicketsExist() {
        // Setup infrastructure entities
        Movie movie = Movie.builder().title("Interstellar").build();
        ScreenSession session = ScreenSession.builder()
                .id(99L)
                .date(tomorrow)
                .startTime(LocalTime.of(20, 0))
                .movie(movie)
                .build();

        User userAlice = User.builder().id(1L).username("alice").build();
        User userBob = User.builder().id(2L).username("bob").build();

        Order orderAlice = Order.builder().id(50L).user(userAlice).build();
        Order orderBob = Order.builder().id(51L).user(userBob).build();

        // Branch 3 & 4 execution: Alice has TWO tickets for the same session; Bob has ONE.
        Ticket ticketAlice1 = Ticket.builder().id(101L).order(orderAlice).screenSession(session).build();
        Ticket ticketAlice2 = Ticket.builder().id(102L).order(orderAlice).screenSession(session).build();
        Ticket ticketBob = Ticket.builder().id(103L).order(orderBob).screenSession(session).build();

        when(screenSessionRepository.findByDate(tomorrow)).thenReturn(List.of(session));
        when(ticketRepository.findByScreenSessionIdAndOrderIsNotNull(99L))
                .thenReturn(List.of(ticketAlice1, ticketAlice2, ticketBob));

        // Act
        notificationScheduler.sendMovieReminders();

        // Assert: Verify that despite 3 tickets, only 2 notifications are saved due to map deduplication filtering
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());

        List<Notification> savedNotifications = notificationCaptor.getAllValues();
        assertThat(savedNotifications).hasSize(2);

        // Verify notification contents & properties
        Notification aliceNotification = savedNotifications.stream()
                .filter(n -> n.getUser().getId().equals(1L))
                .findFirst()
                .orElseThrow();

        assertThat(aliceNotification.getType()).isEqualTo(NotificationType.MOVIE_REMINDER);
        assertThat(aliceNotification.getOrder()).isEqualTo(orderAlice);
        assertThat(aliceNotification.getContent())
                .isEqualTo("Reminder: you have a ticket for \"Interstellar\" tomorrow at 20:00. Enjoy the show!");
        assertThat(aliceNotification.getCreatedDate()).isNotNull();
        assertThat(aliceNotification.getSentDate()).isNotNull();

        Notification bobNotification = savedNotifications.stream()
                .filter(n -> n.getUser().getId().equals(2L))
                .findFirst()
                .orElseThrow();

        assertThat(bobNotification.getUser()).isEqualTo(userBob);
        assertThat(bobNotification.getOrder()).isEqualTo(orderBob);
    }
}