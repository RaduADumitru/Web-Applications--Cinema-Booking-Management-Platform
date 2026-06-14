package com.awbd.cinema.schedulers;

import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.repositories.NotificationRepository;
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

    @Mock private TicketRepository ticketRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private NotificationScheduler notificationScheduler;

    @Captor private ArgumentCaptor<Notification> notificationCaptor;

    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        tomorrow = LocalDate.now().plusDays(1);
    }

    @Test
    void sendMovieReminders_ShouldDoNothing_WhenNoBookedTicketsForTomorrow() {
        when(ticketRepository.findBySessionDateAndOrderIsNotNull(tomorrow)).thenReturn(Collections.emptyList());

        notificationScheduler.sendMovieReminders();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendMovieReminders_ShouldSendOneNotificationPerUserPerSession() {
        Order orderAlice = Order.builder().id(50L).userId(1L).build();
        Order orderBob = Order.builder().id(51L).userId(2L).build();

        Ticket ticketAlice1 = Ticket.builder().id(101L).order(orderAlice).screenSessionId(99L)
                .movieTitle("Interstellar").sessionStartTime(LocalTime.of(20, 0)).build();
        Ticket ticketAlice2 = Ticket.builder().id(102L).order(orderAlice).screenSessionId(99L)
                .movieTitle("Interstellar").sessionStartTime(LocalTime.of(20, 0)).build();
        Ticket ticketBob = Ticket.builder().id(103L).order(orderBob).screenSessionId(99L)
                .movieTitle("Interstellar").sessionStartTime(LocalTime.of(20, 0)).build();

        when(ticketRepository.findBySessionDateAndOrderIsNotNull(tomorrow))
                .thenReturn(List.of(ticketAlice1, ticketAlice2, ticketBob));

        notificationScheduler.sendMovieReminders();

        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        List<Notification> saved = notificationCaptor.getAllValues();
        assertThat(saved).hasSize(2);

        Notification alice = saved.stream().filter(n -> n.getUserId().equals(1L)).findFirst().orElseThrow();
        assertThat(alice.getType()).isEqualTo(NotificationType.MOVIE_REMINDER);
        assertThat(alice.getOrder()).isEqualTo(orderAlice);
        assertThat(alice.getContent())
                .isEqualTo("Reminder: you have a ticket for \"Interstellar\" tomorrow at 20:00. Enjoy the show!");
        assertThat(alice.getCreatedDate()).isNotNull();
        assertThat(alice.getSentDate()).isNotNull();

        Notification bob = saved.stream().filter(n -> n.getUserId().equals(2L)).findFirst().orElseThrow();
        assertThat(bob.getOrder()).isEqualTo(orderBob);
    }
}
