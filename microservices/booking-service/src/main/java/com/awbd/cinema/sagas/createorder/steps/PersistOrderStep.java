package com.awbd.cinema.sagas.createorder.steps;

import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.entities.*;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.sagas.SagaStep;
import com.awbd.cinema.sagas.createorder.CreateOrderContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersistOrderStep implements SagaStep<CreateOrderContext> {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketInfoRepository ticketInfoRepository;
    private final OfferRepository offerRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void execute(CreateOrderContext ctx) {
        List<Ticket> tickets = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalPoints = 0;

        for (OrderItemDTO item : ctx.getDto().items()) {
            Ticket ticket = ticketRepository.findById(item.ticketId())
                    .orElseThrow(() -> new NotFoundException("Ticket " + item.ticketId() + " not found."));
            if (!ticket.isAvailable()) {
                throw new BadRequestException("Ticket " + item.ticketId() + " is no longer available.");
            }
            TicketInfo info = ticketInfoRepository.findByType(item.type())
                    .orElseThrow(() -> new NotFoundException("No price configured for type '" + item.type() + "'."));

            ticket.setType(item.type());
            ticket.setTicketInfo(info);
            ticket.setAvailable(false);

            totalPrice = totalPrice.add(info.getPrice().add(ticket.getExtraFee()));
            totalPoints += ticket.getExtraPoints() + ticket.getSessionPoints();
            tickets.add(ticket);
        }

        if (ctx.getPointsDiscount() != null) {
            totalPrice = totalPrice.subtract(ctx.getPointsDiscount()).max(BigDecimal.ZERO);
        }

        Offer offer = offerRepository.findByDay(LocalDateTime.now().getDayOfWeek()).orElse(null);
        if (offer != null) {
            BigDecimal offerDiscount = totalPrice
                    .multiply(BigDecimal.valueOf(offer.getPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalPrice = totalPrice.subtract(offerDiscount).max(BigDecimal.ZERO);
        }

        Order order = Order.builder()
                .createdAt(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .price(totalPrice)
                .loyaltyPoints(totalPoints)
                .pointsSpend(ctx.getPointsSpend())
                .offer(offer)
                .userId(ctx.getUserId())
                .build();

        Order savedOrder = orderRepository.save(order);

        for (Ticket ticket : tickets) {
            ticket.setOrder(savedOrder);
            ticketRepository.save(ticket);
        }

        savedOrder.setTickets(tickets);

        String ticketDetails = tickets.stream()
                .map(t -> "\"" + t.getMovieTitle() + "\""
                        + " on " + t.getSessionDate()
                        + " at " + t.getSessionStartTime()
                        + ", Row " + t.getSeatRow()
                        + " Seat " + t.getSeatNumber()
                        + " (" + t.getSeatZone() + ")"
                        + " [" + t.getType() + "]")
                .collect(Collectors.joining("\n- ", "- ", ""));

        notificationRepository.save(Notification.builder()
                .type(NotificationType.TICKET_BOUGHT)
                .content("Your order has been confirmed! You purchased " + tickets.size()
                        + " ticket(s):\n" + ticketDetails)
                .createdDate(LocalDateTime.now())
                .sentDate(LocalDateTime.now())
                .userId(ctx.getUserId())
                .order(savedOrder)
                .build());

        ctx.setSavedOrder(savedOrder);
        log.info("Order {} persisted for user {}.", savedOrder.getId(), ctx.getUserId());
    }

    @Override
    @Transactional
    public void compensate(CreateOrderContext ctx) {
        if (ctx.getSavedOrder() == null) return;

        Order order = orderRepository.findById(ctx.getSavedOrder().getId()).orElse(null);
        if (order == null) return;

        order.setDeletedAt(LocalDateTime.now());
        if (order.getTickets() != null) {
            for (Ticket ticket : order.getTickets()) {
                ticket.setAvailable(true);
                ticket.setType(null);
                ticket.setOrder(null);
                ticketRepository.save(ticket);
            }
        }
        orderRepository.save(order);
        log.info("Order {} soft-deleted and tickets released during compensation.", order.getId());
    }
}
