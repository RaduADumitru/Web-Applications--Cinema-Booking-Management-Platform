package com.awbd.cinema.schedulers;

import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final ScreenSessionRepository screenSessionRepository;
    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendMovieReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<ScreenSession> sessions = screenSessionRepository.findByDate(tomorrow);

        for (ScreenSession session : sessions) {
            List<Ticket> bookedTickets = ticketRepository.findByScreenSessionIdAndOrderIsNotNull(session.getId());

            // one notification per user per session, linked to their order for that session
            Map<Long, Order> userOrderMap = new HashMap<>();
            for (Ticket ticket : bookedTickets) {
                userOrderMap.putIfAbsent(ticket.getOrder().getUser().getId(), ticket.getOrder());
            }

            for (Order order : userOrderMap.values()) {
                User user = order.getUser();
                String content = "Reminder: you have a ticket for \"" + session.getMovie().getTitle()
                        + "\" tomorrow at " + session.getStartTime() + ". Enjoy the show!";

                Notification notification = Notification.builder()
                        .type(NotificationType.MOVIE_REMINDER)
                        .content(content)
                        .createdDate(LocalDateTime.now())
                        .sentDate(LocalDateTime.now())
                        .user(user)
                        .order(order)
                        .build();

                notificationRepository.save(notification);
            }
        }
    }
}
