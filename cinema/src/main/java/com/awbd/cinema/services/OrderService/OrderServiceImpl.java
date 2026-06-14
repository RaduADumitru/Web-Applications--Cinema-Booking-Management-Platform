package com.awbd.cinema.services.OrderService;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.entities.*;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.utils.RestPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketInfoRepository ticketInfoRepository;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "ticket_lists", allEntries = true),
            @CacheEvict(value = "single_ticket", allEntries = true),
            @CacheEvict(value = "user_discount_previews", key = "#userId"),
            @CacheEvict(value = "user_notifications", key = "#userId")
    })
    public OrderDTO createOrder(CreateOrderDTO dto, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        List<Ticket> tickets = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalPoints = 0;

        for (OrderItemDTO item : dto.items()) {
            Ticket ticket = ticketRepository.findById(item.ticketId())
                    .orElseThrow(() -> new NotFoundException("Ticket " + item.ticketId() + " not found."));

            if (!ticket.isAvailable()) {
                log.warn("User {} attempted to order ticket {} which is no longer available.", userId, item.ticketId());
                throw new BadRequestException("Ticket " + item.ticketId() + " is no longer available.");
            }

            TicketInfo info = ticketInfoRepository.findByType(item.type())
                    .orElseThrow(() -> new NotFoundException(
                            "No price configured for type '" + item.type() + "'."));

            BigDecimal ticketPrice = info.getPrice();
            int ticketPoints = 0;

            if (ticket.getSeat().getCategory() != null) {
                ticketPrice = ticketPrice.add(ticket.getSeat().getCategory().getExtraFee());
                ticketPoints += ticket.getSeat().getCategory().getExtraPoints();
            }
            if (ticket.getScreenSession().getSessionInfo() != null) {
                ticketPoints += ticket.getScreenSession().getSessionInfo().getPoints();
            }

            ticket.setType(item.type());
            ticket.setTicketInfo(info);
            ticket.setAvailable(false);
            totalPrice = totalPrice.add(ticketPrice);
            totalPoints += ticketPoints;
            tickets.add(ticket);
        }

        PointsSpend pointsSpend = null;
        if (dto.useDiscount() && user.getLoyaltyPoints() > 0) {
            int pointsToSpend = user.getLoyaltyPoints();
            BigDecimal discount = PointsSpend.calculateDiscount(pointsToSpend);
            pointsSpend = PointsSpend.builder()
                    .pointsUsed(pointsToSpend)
                    .discount(discount)
                    .build();
            totalPrice = totalPrice.subtract(discount).max(BigDecimal.ZERO);
            user.setLoyaltyPoints(0);
            userRepository.save(user);
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
                .pointsSpend(pointsSpend)
                .offer(offer)
                .user(user)
                .build();

        Order savedOrder = orderRepository.save(order);

        for (Ticket ticket : tickets) {
            ticket.setOrder(savedOrder);
            ticketRepository.save(ticket);
        }

        savedOrder.setTickets(tickets);

        String ticketDetails = tickets.stream()
                .map(t -> "\"" + t.getScreenSession().getMovie().getTitle() + "\""
                        + " on " + t.getScreenSession().getDate()
                        + " at " + t.getScreenSession().getStartTime()
                        + ", Row " + t.getSeat().getRow()
                        + " Seat " + t.getSeat().getNumber()
                        + " (" + t.getSeat().getZone() + ")"
                        + " [" + t.getType() + "]")
                .collect(Collectors.joining("\n- ", "- ", ""));

        Notification ticketBoughtNotification = Notification.builder()
                .type(NotificationType.TICKET_BOUGHT)
                .content("Your order has been confirmed! You purchased " + tickets.size()
                        + " ticket(s):\n" + ticketDetails)
                .createdDate(LocalDateTime.now())
                .sentDate(LocalDateTime.now())
                .user(user)
                .order(savedOrder)
                .build();
        notificationRepository.save(ticketBoughtNotification);

        return OrderDTO.from(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "order_lists")
    public RestPage<OrderDTO> getOrders(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            return new RestPage<>(orderRepository.findByStatus(orderStatus, pageable).map(OrderDTO::from));
        }
        return new RestPage<>(orderRepository.findAll(pageable).map(OrderDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_orders")
    public RestPage<OrderDTO> getMyOrders(Long userId, Pageable pageable) {
        return new RestPage<>(orderRepository.findByUserId(userId, pageable).map(OrderDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_discount_previews", key = "#userId")
    public DiscountPreviewDTO getDiscountPreview(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        int points = user.getLoyaltyPoints();
        BigDecimal discount = PointsSpend.calculateDiscount(points);
        return new DiscountPreviewDTO(points, discount);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_past_orders")
    public RestPage<OrderDTO> getMyPastOrders(Long userId, Pageable pageable) {
        return new RestPage<>(orderRepository.findByUserIdAndStatusIn(
                userId, List.of(OrderStatus.PAID, OrderStatus.CANCELLED), pageable)
                .map(OrderDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "single_orders", key = "#id")
    public OrderDTO getOrder(Long id) {
        return orderRepository.findById(id)
                .filter(o -> o.getDeletedAt() == null)
                .map(OrderDTO::from)
                .orElseThrow(() -> new NotFoundException("Order not found."));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_orders", key = "#id"),
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "user_past_orders", allEntries = true),
            @CacheEvict(value = "user_discount_previews", key = "#result.userId()")
    })
    public OrderDTO payOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Only PENDING orders can be paid.");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaymentAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        User user = saved.getUser();
        user.setLoyaltyPoints(user.getLoyaltyPoints() + saved.getLoyaltyPoints());
        userRepository.save(user);

        return OrderDTO.from(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_orders", key = "#id"),
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "user_past_orders", allEntries = true),
            @CacheEvict(value = "ticket_lists", allEntries = true),
            @CacheEvict(value = "single_ticket", allEntries = true)
    })
    public OrderDTO cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order is already cancelled.");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BadRequestException("Paid orders cannot be cancelled.");
        }
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getTickets() != null) {
            for (Ticket ticket : order.getTickets()) {
                ticket.setAvailable(true);
                ticket.setType(null);
                ticket.setOrder(null);
                ticketRepository.save(ticket);
            }
        }
        return OrderDTO.from(orderRepository.save(order));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_orders", key = "#id"),
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "user_past_orders", allEntries = true)
    })
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        order.setDeletedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
}
