package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceGateway;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.sagas.createorder.CreateOrderSagaOrchestrator;
import com.awbd.cinema.sagas.payorder.PayOrderSagaOrchestrator;
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
    @Mock private UserServiceGateway userServiceGateway;
    @Mock private CreateOrderSagaOrchestrator createOrderSagaOrchestrator;
    @Mock private PayOrderSagaOrchestrator payOrderSagaOrchestrator;

    @InjectMocks private OrderServiceImpl orderService;

    private static final Long USER_ID = 1L;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
    }

    private Ticket availableTicket(Long id) {
        return Ticket.builder()
                .id(id).isAvailable(true)
                .seatId(10L).roomId(20L).screenSessionId(30L)
                .seatRow(1).seatNumber(1).seatZone("A")
                .extraFee(BigDecimal.ZERO).extraPoints(0).sessionPoints(0)
                .movieTitle("Test Movie").sessionDate(LocalDate.now()).sessionStartTime(LocalTime.NOON)
                .build();
    }

    @Nested
    @DisplayName("createOrder Tests")
    class CreateOrderTests {

        @Test
        void createOrder_TicketNotFound_ThrowsNotFoundException() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(99L, TicketType.ADULT)), false);
            when(createOrderSagaOrchestrator.createOrder(dto, USER_ID))
                    .thenThrow(new NotFoundException("Ticket 99 not found."));

            assertThatThrownBy(() -> orderService.createOrder(dto, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Ticket 99 not found.");
        }

        @Test
        void createOrder_TicketNotAvailable_ThrowsBadRequestException() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(1L, TicketType.ADULT)), false);
            when(createOrderSagaOrchestrator.createOrder(dto, USER_ID))
                    .thenThrow(new BadRequestException("Ticket 1 is no longer available."));

            assertThatThrownBy(() -> orderService.createOrder(dto, USER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Ticket 1 is no longer available.");
        }

        @Test
        @DisplayName("Should delegate to CreateOrderSagaOrchestrator and return its result")
        void createOrder_Success_NoDiscountNoOffer() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(1L, TicketType.ADULT)), false);
            Order savedOrder = Order.builder().id(10L).status(OrderStatus.PENDING)
                    .price(BigDecimal.valueOf(50.00)).userId(USER_ID).build();
            OrderDTO expected = OrderDTO.from(savedOrder);
            when(createOrderSagaOrchestrator.createOrder(dto, USER_ID)).thenReturn(expected);

            OrderDTO result = orderService.createOrder(dto, USER_ID);

            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.price()).isEqualByComparingTo("50.00");
            verify(createOrderSagaOrchestrator).createOrder(dto, USER_ID);
        }

        @Test
        @DisplayName("Should delegate to CreateOrderSagaOrchestrator when using loyalty discount")
        void createOrder_Success_WithLoyaltyDiscount() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(1L, TicketType.ADULT)), true);
            Order savedOrder = Order.builder().id(10L).status(OrderStatus.PENDING)
                    .price(BigDecimal.valueOf(40.00)).userId(USER_ID).build();
            OrderDTO expected = OrderDTO.from(savedOrder);
            when(createOrderSagaOrchestrator.createOrder(dto, USER_ID)).thenReturn(expected);

            OrderDTO result = orderService.createOrder(dto, USER_ID);

            assertThat(result.price()).isEqualByComparingTo("40.00");
            verify(createOrderSagaOrchestrator).createOrder(dto, USER_ID);
        }
    }

    @Nested
    @DisplayName("getOrders and Query Tests")
    class GetOrdersTests {

        @Test
        void getOrders_BlankStatus_ReturnsAllOrders() {
            Order order = Order.builder().id(1L).userId(USER_ID).price(BigDecimal.TEN).build();
            when(orderRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderDTO> result = orderService.getOrders("", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository, times(1)).findAll(pageable);
        }

        @Test
        void getOrders_WithValidStatus_ReturnsFilteredOrders() {
            Order order = Order.builder().id(1L).status(OrderStatus.PAID).userId(USER_ID).price(BigDecimal.TEN).build();
            when(orderRepository.findByStatus(OrderStatus.PAID, pageable)).thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderDTO> result = orderService.getOrders("paid", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository, times(1)).findByStatus(OrderStatus.PAID, pageable);
        }

        @Test
        void getDiscountPreview_ValidUser_ReturnsCorrectPreview() {
            when(userServiceGateway.getLoyaltyPoints(USER_ID)).thenReturn(new LoyaltyPointsDTO(USER_ID, 50));

            DiscountPreviewDTO preview = orderService.getDiscountPreview(USER_ID);

            assertThat(preview.loyaltyPoints()).isEqualTo(50);
            assertThat(preview.potentialDiscount()).isEqualByComparingTo("5.00");
        }
    }

    @Nested
    @DisplayName("Order State Action Tests")
    class OrderStateActionTests {

        @Test
        void payOrder_PendingOrder_SuccessfullyDelegatesToSaga() {
            Order paidOrder = Order.builder().id(5L).status(OrderStatus.PAID)
                    .loyaltyPoints(15).price(BigDecimal.valueOf(100.00)).userId(USER_ID).build();
            OrderDTO expected = OrderDTO.from(paidOrder);
            when(payOrderSagaOrchestrator.payOrder(5L)).thenReturn(expected);

            OrderDTO result = orderService.payOrder(5L);

            assertThat(result.status()).isEqualTo(OrderStatus.PAID);
            verify(payOrderSagaOrchestrator).payOrder(5L);
        }

        @Test
        void payOrder_NotPending_ThrowsBadRequestException() {
            when(payOrderSagaOrchestrator.payOrder(5L))
                    .thenThrow(new BadRequestException("Only PENDING orders can be paid."));

            assertThatThrownBy(() -> orderService.payOrder(5L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Only PENDING orders can be paid.");
        }

        @Test
        void cancelOrder_PendingOrder_Success() {
            Ticket securedTicket = Ticket.builder().id(100L).isAvailable(false).type(TicketType.ADULT).build();
            List<Ticket> mutableTickets = new ArrayList<>(List.of(securedTicket));
            Order pendingOrder = Order.builder().id(5L).status(OrderStatus.PENDING)
                    .tickets(mutableTickets).userId(USER_ID).price(BigDecimal.TEN).build();
            securedTicket.setOrder(pendingOrder);

            when(orderRepository.findById(5L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDTO result = orderService.cancelOrder(5L);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(securedTicket.isAvailable()).isTrue();
            assertThat(securedTicket.getType()).isNull();
            assertThat(securedTicket.getOrder()).isNull();
            verify(ticketRepository, times(1)).save(securedTicket);
        }

        @Test
        void deleteOrder_ValidOrder_SetsDeletedAt() {
            Order order = Order.builder().id(5L).userId(USER_ID).build();
            when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

            orderService.deleteOrder(5L);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getDeletedAt()).isNotNull();
        }
    }
}
