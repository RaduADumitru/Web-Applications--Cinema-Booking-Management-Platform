package com.awbd.cinema.schedulers;

import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendMovieReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Ticket> bookedTickets = ticketRepository.findBySessionDateAndOrderIsNotNull(tomorrow);

        // one notification per user per session, using the denormalized snapshot fields on the ticket
        Map<String, Ticket> oneTicketPerSessionUser = new LinkedHashMap<>();
        for (Ticket ticket : bookedTickets) {
            Long userId = ticket.getOrder().getUserId();
            String key = ticket.getScreenSessionId() + ":" + userId;
            oneTicketPerSessionUser.putIfAbsent(key, ticket);
        }

        for (Ticket ticket : oneTicketPerSessionUser.values()) {
            Order order = ticket.getOrder();
            String content = "Reminder: you have a ticket for \"" + ticket.getMovieTitle()
                    + "\" tomorrow at " + ticket.getSessionStartTime() + ". Enjoy the show!";

            Notification notification = Notification.builder()
                    .type(NotificationType.MOVIE_REMINDER)
                    .content(content)
                    .createdDate(LocalDateTime.now())
                    .sentDate(LocalDateTime.now())
                    .userId(order.getUserId())
                    .order(order)
                    .build();

            notificationRepository.save(notification);
        }
    }
}
