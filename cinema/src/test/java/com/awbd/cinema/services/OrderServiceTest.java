package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.entities.*;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.services.OrderService.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketInfoRepository ticketInfoRepository;
    @Mock private UserRepository userRepository;
    @Mock private OfferRepository offerRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ScreenSessionRepository screenSessionRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User sampleUser;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("john_doe")
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .loyaltyPoints(100)
                .build();

        pageable = PageRequest.of(0, 10);
    }

    // ==========================================
    // CREATE ORDER TESTS
    // ==========================================
    @Nested
    @DisplayName("createOrder Tests")
    class CreateOrderTests {

        @Test
        @DisplayName("Should throw NotFoundException when user does not exist")
        void createOrder_UserNotFound_ThrowsNotFoundException() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(1L, TicketType.ADULT)), false);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(dto, 1L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found.");
        }

        @Test
        @DisplayName("Should throw NotFoundException when ticket does not exist")
        void createOrder_TicketNotFound_ThrowsNotFoundException() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(99L, TicketType.ADULT)), false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(dto, 1L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Ticket 99 not found.");
        }

        @Test
        @DisplayName("Should throw BadRequestException when ticket is not available")
        void createOrder_TicketNotAvailable_ThrowsBadRequestException() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(1L, TicketType.ADULT)), false);
            Ticket unavailableTicket = Ticket.builder().id(1L).isAvailable(false).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(unavailableTicket));

            assertThatThrownBy(() -> orderService.createOrder(dto, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Ticket 1 is no longer available.");
        }

        @Test
        @DisplayName("Should successfully create order without discounts or offers")
        void createOrder_Success_NoDiscountNoOffer() {
            OrderItemDTO itemDto = new OrderItemDTO(1L, TicketType.ADULT);
            CreateOrderDTO dto = new CreateOrderDTO(List.of(itemDto), false);

            // Stub deep dependencies for Ticket properties safely
            Ticket ticket = mock(Ticket.class);
            Seat seat = mock(Seat.class);
            ScreenSession screenSession = mock(ScreenSession.class);
            Movie movie = mock(Movie.class);

            when(ticket.getId()).thenReturn(1L);
            when(ticket.isAvailable()).thenReturn(true);
            when(ticket.getSeat()).thenReturn(seat);
            when(ticket.getScreenSession()).thenReturn(screenSession);
            when(screenSession.getId()).thenReturn(3L);
            when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(screenSession));
            when(screenSession.getMovie()).thenReturn(movie);
            when(screenSession.getDate()).thenReturn(LocalDate.now());
            when(screenSession.getStartTime()).thenReturn(LocalTime.now());

            TicketInfo ticketInfo = TicketInfo.builder().type(TicketType.ADULT).price(BigDecimal.valueOf(50.00)).build();
            Order savedOrder = Order.builder()
                    .id(10L)
                    .status(OrderStatus.PENDING)
                    .price(BigDecimal.valueOf(50.00))
                    .user(sampleUser)
                    .tickets(List.of(ticket))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.of(ticketInfo));
            when(offerRepository.findByDay(any(DayOfWeek.class))).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderDTO result = orderService.createOrder(dto, 1L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.price()).isEqualByComparingTo("50.00");
            verify(notificationRepository, times(1)).save(any(Notification.class));
        }

        @Test
        @DisplayName("Should apply loyalty discount and reset user points when requested")
        void createOrder_Success_WithLoyaltyDiscount() {
            OrderItemDTO itemDto = new OrderItemDTO(1L, TicketType.ADULT);
            CreateOrderDTO dto = new CreateOrderDTO(List.of(itemDto), true); // useDiscount = true

            Ticket ticket = mock(Ticket.class);
            Seat seat = mock(Seat.class);
            ScreenSession screenSession = mock(ScreenSession.class);
            Movie movie = mock(Movie.class);

            when(ticket.isAvailable()).thenReturn(true);
            when(ticket.getSeat()).thenReturn(seat);
            when(ticket.getScreenSession()).thenReturn(screenSession);
            when(screenSession.getId()).thenReturn(3L);
            when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(screenSession));
            when(screenSession.getMovie()).thenReturn(movie);

            TicketInfo ticketInfo = TicketInfo.builder().type(TicketType.ADULT).price(BigDecimal.valueOf(50.00)).build();
            sampleUser.setLoyaltyPoints(100); // 100 points = 10.00 discount

            Order savedOrder = Order.builder()
                    .id(10L)
                    .status(OrderStatus.PENDING)
                    .price(BigDecimal.valueOf(40.00))
                    .user(sampleUser)
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.of(ticketInfo));
            when(offerRepository.findByDay(any(DayOfWeek.class))).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            orderService.createOrder(dto, 1L);

            assertThat(sampleUser.getLoyaltyPoints()).isZero();
            verify(userRepository, times(1)).save(sampleUser);
        }

        @Test
        @DisplayName("Should reject ordering a ticket whose session's movie is soft-deleted")
        void createOrder_TicketSessionMovieSoftDeleted_ThrowsBadRequest() {
            OrderItemDTO itemDto = new OrderItemDTO(1L, TicketType.ADULT);
            CreateOrderDTO dto = new CreateOrderDTO(List.of(itemDto), false);

            Ticket ticket = mock(Ticket.class);
            ScreenSession screenSession = mock(ScreenSession.class);

            when(ticket.isAvailable()).thenReturn(true);
            when(ticket.getScreenSession()).thenReturn(screenSession);
            when(screenSession.getId()).thenReturn(3L);
            // findActiveById empty => the session's movie is soft-deleted (hidden).
            when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.empty());

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> orderService.createOrder(dto, 1L))
                    .isInstanceOf(BadRequestException.class);
            verify(orderRepository, never()).save(any());
        }
    }

    // ==========================================
    // GET ORDERS & HISTORIES
    // ==========================================
    @Nested
    @DisplayName("getOrders and Query Tests")
    class GetOrdersTests {

        @Test
        @DisplayName("Should fetch all orders when status parameter is blank")
        void getOrders_BlankStatus_ReturnsAllOrders() {
            Order order = Order.builder().id(1L).user(sampleUser).price(BigDecimal.TEN).build();
            Page<Order> page = new PageImpl<>(List.of(order));

            when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

            Page<OrderDTO> result = orderService.getOrders("", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository, times(1)).findAll(pageable);
        }

        @Test
        @DisplayName("Should fetch filtered orders when status parameter is valid")
        void getOrders_WithValidStatus_ReturnsFilteredOrders() {
            Order order = Order.builder().id(1L).status(OrderStatus.PAID).user(sampleUser).price(BigDecimal.TEN).build();
            Page<Order> page = new PageImpl<>(List.of(order));

            when(orderRepository.findByStatus(OrderStatus.PAID, pageable)).thenReturn(page);

            Page<OrderDTO> result = orderService.getOrders("paid", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository, times(1)).findByStatus(OrderStatus.PAID, pageable);
        }

        @Test
        @DisplayName("Should return preview calculations for user discount")
        void getDiscountPreview_ValidUser_ReturnsCorrectPreview() {
            sampleUser.setLoyaltyPoints(50); // 50 / 10 = 5.00 discount
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            DiscountPreviewDTO preview = orderService.getDiscountPreview(1L);

            assertThat(preview.loyaltyPoints()).isEqualTo(50);
            assertThat(preview.potentialDiscount()).isEqualByComparingTo("5.00");
        }
    }

    // ==========================================
    // ORDER STATE MUTATION TESTS (PAY, CANCEL, DELETE)
    // ==========================================
    @Nested
    @DisplayName("Order State Action Tests")
    class OrderStateActionTests {

        @Test
        @DisplayName("Should successfully capture payment and credit loyalty points to user")
        void payOrder_PendingOrder_Success() {
            Order pendingOrder = Order.builder()
                    .id(5L)
                    .status(OrderStatus.PENDING)
                    .loyaltyPoints(15)
                    .price(BigDecimal.valueOf(100.00))
                    .user(sampleUser)
                    .build();

            sampleUser.setLoyaltyPoints(10);

            when(orderRepository.findById(5L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OrderDTO result = orderService.payOrder(5L);

            assertThat(result.status()).isEqualTo(OrderStatus.PAID);
            assertThat(sampleUser.getLoyaltyPoints()).isEqualTo(25); // 10 initial + 15 earned
            verify(userRepository).save(sampleUser);
        }

        @Test
        @DisplayName("Should throw BadRequestException when paying a non-pending order")
        void payOrder_NotPending_ThrowsBadRequestException() {
            Order paidOrder = Order.builder().id(5L).status(OrderStatus.PAID).build();
            when(orderRepository.findById(5L)).thenReturn(Optional.of(paidOrder));

            assertThatThrownBy(() -> orderService.payOrder(5L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Only PENDING orders can be paid.");
        }

        @Test
        @DisplayName("Should release tickets and set status to cancelled when processing a pending order")
        void cancelOrder_PendingOrder_Success() {
            Ticket securedTicket = Ticket.builder().id(100L).isAvailable(false).type(TicketType.ADULT).build();
            List<Ticket> mutableTickets = new ArrayList<>(List.of(securedTicket));

            Order pendingOrder = Order.builder()
                    .id(5L)
                    .status(OrderStatus.PENDING)
                    .tickets(mutableTickets)
                    .user(sampleUser)
                    .price(BigDecimal.TEN)
                    .build();
            securedTicket.setOrder(pendingOrder);

            when(orderRepository.findById(5L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OrderDTO result = orderService.cancelOrder(5L);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(securedTicket.isAvailable()).isTrue();
            assertThat(securedTicket.getType()).isNull();
            assertThat(securedTicket.getOrder()).isNull();
            verify(ticketRepository, times(1)).save(securedTicket);
        }

        @Test
        @DisplayName("Should perform logical soft deletion by setting deletedAt date timestamp")
        void deleteOrder_ValidOrder_SetsDeletedAt() {
            Order order = Order.builder().id(5L).user(sampleUser).build();
            when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

            orderService.deleteOrder(5L);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());

            assertThat(orderCaptor.getValue().getDeletedAt()).isNotNull();
        }
    }
}