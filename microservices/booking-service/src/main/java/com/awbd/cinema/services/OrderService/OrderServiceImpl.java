package com.awbd.cinema.services.OrderService;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceGateway;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.PointsSpend;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.repositories.TicketRepository;
import com.awbd.cinema.sagas.createorder.CreateOrderSagaOrchestrator;
import com.awbd.cinema.sagas.payorder.PayOrderSagaOrchestrator;
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
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final UserServiceGateway userServiceGateway;
    private final CreateOrderSagaOrchestrator createOrderSagaOrchestrator;
    private final PayOrderSagaOrchestrator payOrderSagaOrchestrator;

    @Override
    @Caching(evict = {
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "ticket_lists", allEntries = true),
            @CacheEvict(value = "single_ticket", allEntries = true),
            @CacheEvict(value = "user_discount_previews", key = "#userId"),
            @CacheEvict(value = "user_notifications", key = "#userId")
    })
    public OrderDTO createOrder(CreateOrderDTO dto, Long userId) {
        return createOrderSagaOrchestrator.createOrder(dto, userId);
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
        LoyaltyPointsDTO loyalty = userServiceGateway.getLoyaltyPoints(userId);
        int points = loyalty.loyaltyPoints();
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
    @Caching(evict = {
            @CacheEvict(value = "single_orders", key = "#id"),
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "user_past_orders", allEntries = true),
            @CacheEvict(value = "user_discount_previews", key = "#result.userId()")
    })
    public OrderDTO payOrder(Long id) {
        return payOrderSagaOrchestrator.payOrder(id);
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
