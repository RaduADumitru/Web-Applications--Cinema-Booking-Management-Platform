package com.awbd.cinema.sagas;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceGateway;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.SagaInstance;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.enums.SagaStatus;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.sagas.createorder.CreateOrderSagaOrchestrator;
import com.awbd.cinema.sagas.createorder.steps.DeductPointsStep;
import com.awbd.cinema.sagas.createorder.steps.PersistOrderStep;
import com.awbd.cinema.sagas.payorder.PayOrderSagaOrchestrator;
import com.awbd.cinema.sagas.payorder.steps.AwardPointsStep;
import com.awbd.cinema.sagas.payorder.steps.MarkOrderPaidStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaCompensationTest {

    @Mock SagaInstanceRepository sagaInstanceRepository;
    @Mock UserServiceGateway userServiceGateway;

    // CreateOrderSaga dependencies
    @Mock TicketRepository ticketRepository;
    @Mock TicketInfoRepository ticketInfoRepository;
    @Mock OrderRepository orderRepository;
    @Mock OfferRepository offerRepository;
    @Mock NotificationRepository notificationRepository;

    CreateOrderSagaOrchestrator createOrchestrator;
    PayOrderSagaOrchestrator payOrchestrator;

    @BeforeEach
    void setUp() {
        // sagaInstanceRepository.save() must return the entity so the orchestrator can update it
        when(sagaInstanceRepository.save(any(SagaInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DeductPointsStep deductStep = new DeductPointsStep(userServiceGateway);
        PersistOrderStep persistStep = new PersistOrderStep(
                orderRepository, ticketRepository, ticketInfoRepository,
                offerRepository, notificationRepository);
        createOrchestrator = new CreateOrderSagaOrchestrator(sagaInstanceRepository, deductStep, persistStep);

        MarkOrderPaidStep markPaidStep = new MarkOrderPaidStep(orderRepository);
        AwardPointsStep awardStep = new AwardPointsStep(userServiceGateway);
        payOrchestrator = new PayOrderSagaOrchestrator(sagaInstanceRepository, markPaidStep, awardStep);
    }


    @Nested
    @DisplayName("CreateOrderSaga compensation")
    class CreateOrderSagaCompensation {

        @Test
        @DisplayName("Points are restored when PersistOrderStep fails after DeductPointsStep succeeds")
        void persistFails_pointsAreRestored() {
            Long userId = 1L;
            CreateOrderDTO dto = new CreateOrderDTO(
                    List.of(new OrderItemDTO(99L, TicketType.ADULT)), true);

            // Step 0 (DeductPoints) will succeed — user has 100 points
            when(userServiceGateway.getLoyaltyPoints(userId))
                    .thenReturn(new LoyaltyPointsDTO(userId, 100));
            when(userServiceGateway.updateLoyaltyPointsStrict(eq(userId), any()))
                    .thenReturn(new LoyaltyPointsDTO(userId, 0));

            // Step 1 (PersistOrder) fails — ticket not found
            when(ticketRepository.findById(99L))
                    .thenThrow(new NotFoundException("Ticket 99 not found."));

            assertThatThrownBy(() -> createOrchestrator.createOrder(dto, userId))
                    .isInstanceOf(RuntimeException.class);

            // Compensation must call updateLoyaltyPointsStrict to restore the original 100 points
            verify(userServiceGateway).updateLoyaltyPointsStrict(
                    eq(userId), argThat(d -> d.loyaltyPoints() == 100));

            // No order must have been committed
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Saga instance is saved with COMPENSATED status after successful compensation")
        void persistFails_sagaMarkedCompensated() {
            Long userId = 1L;
            CreateOrderDTO dto = new CreateOrderDTO(
                    List.of(new OrderItemDTO(99L, TicketType.ADULT)), true);

            when(userServiceGateway.getLoyaltyPoints(userId))
                    .thenReturn(new LoyaltyPointsDTO(userId, 100));
            when(userServiceGateway.updateLoyaltyPointsStrict(eq(userId), any()))
                    .thenReturn(new LoyaltyPointsDTO(userId, 0));
            when(ticketRepository.findById(99L))
                    .thenThrow(new NotFoundException("Ticket 99 not found."));

            assertThatThrownBy(() -> createOrchestrator.createOrder(dto, userId))
                    .isInstanceOf(RuntimeException.class);

            ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(sagaInstanceRepository, atLeast(2)).save(captor.capture());
            SagaInstance lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(lastSave.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        }

        @Test
        @DisplayName("No points calls are made when useDiscount=false and PersistOrderStep fails")
        void persistFails_noDiscount_noPointsTouched() {
            Long userId = 1L;
            CreateOrderDTO dto = new CreateOrderDTO(
                    List.of(new OrderItemDTO(99L, TicketType.ADULT)), false);

            when(ticketRepository.findById(99L))
                    .thenThrow(new NotFoundException("Ticket 99 not found."));

            assertThatThrownBy(() -> createOrchestrator.createOrder(dto, userId))
                    .isInstanceOf(RuntimeException.class);

            // DeductPointsStep skips when useDiscount=false, so compensation must not touch points
            verify(userServiceGateway, never()).updateLoyaltyPointsStrict(any(), any());
            verify(userServiceGateway, never()).getLoyaltyPoints(any());
        }

        @Test
        @DisplayName("Points are restored when ticket is unavailable (BadRequestException in step 1)")
        void ticketUnavailable_pointsAreRestored() {
            Long userId = 1L;
            CreateOrderDTO dto = new CreateOrderDTO(
                    List.of(new OrderItemDTO(1L, TicketType.ADULT)), true);

            when(userServiceGateway.getLoyaltyPoints(userId))
                    .thenReturn(new LoyaltyPointsDTO(userId, 50));
            when(userServiceGateway.updateLoyaltyPointsStrict(eq(userId), any()))
                    .thenReturn(new LoyaltyPointsDTO(userId, 0));

            // Ticket exists but is no longer available
            Ticket unavailableTicket = Ticket.builder()
                    .id(1L).isAvailable(false).build();
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(unavailableTicket));

            assertThatThrownBy(() -> createOrchestrator.createOrder(dto, userId))
                    .isInstanceOf(RuntimeException.class);

            // Compensation restores the original 50 points
            verify(userServiceGateway).updateLoyaltyPointsStrict(
                    eq(userId), argThat(d -> d.loyaltyPoints() == 50));
        }
    }



    @Nested
    @DisplayName("PayOrderSaga compensation")
    class PayOrderSagaCompensation {

        @Test
        @DisplayName("Order is reverted to PENDING when AwardPointsStep fails after MarkOrderPaidStep succeeds")
        void awardPointsFails_orderRevertedToPending() {
            Long orderId = 5L;
            Order pendingOrder = Order.builder()
                    .id(orderId).status(OrderStatus.PENDING)
                    .loyaltyPoints(10).userId(1L)
                    .price(BigDecimal.valueOf(50))
                    .build();

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Step 1 (AwardPoints) fails — user-service is down
            when(userServiceGateway.getLoyaltyPoints(1L))
                    .thenReturn(new LoyaltyPointsDTO(1L, 20));
            when(userServiceGateway.updateLoyaltyPointsStrict(eq(1L), any()))
                    .thenThrow(new RuntimeException("user-service unavailable"));

            assertThatThrownBy(() -> payOrchestrator.payOrder(orderId))
                    .isInstanceOf(RuntimeException.class);

            // Compensation must revert the order back to PENDING
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, atLeast(2)).save(captor.capture());
            Order lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(lastSave.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(lastSave.getPaymentAt()).isNull();
        }

        @Test
        @DisplayName("Saga instance is saved with COMPENSATED status after successful compensation")
        void awardPointsFails_sagaMarkedCompensated() {
            Long orderId = 5L;
            Order pendingOrder = Order.builder()
                    .id(orderId).status(OrderStatus.PENDING)
                    .loyaltyPoints(10).userId(1L)
                    .price(BigDecimal.valueOf(50))
                    .build();

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userServiceGateway.getLoyaltyPoints(1L))
                    .thenReturn(new LoyaltyPointsDTO(1L, 20));
            when(userServiceGateway.updateLoyaltyPointsStrict(eq(1L), any()))
                    .thenThrow(new RuntimeException("user-service unavailable"));

            assertThatThrownBy(() -> payOrchestrator.payOrder(orderId))
                    .isInstanceOf(RuntimeException.class);

            ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(sagaInstanceRepository, atLeast(2)).save(captor.capture());
            SagaInstance lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(lastSave.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        }

        @Test
        @DisplayName("No compensation runs when MarkOrderPaidStep itself fails (order was never touched)")
        void markPaidFails_noCompensationNeeded() {
            Long orderId = 5L;
            Order alreadyPaid = Order.builder()
                    .id(orderId).status(OrderStatus.PAID).build();

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(alreadyPaid));

            assertThatThrownBy(() -> payOrchestrator.payOrder(orderId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Only PENDING orders can be paid.");

            // AwardPoints was never called because step 0 already failed
            verify(userServiceGateway, never()).updateLoyaltyPointsStrict(any(), any());

            // orderRepository.save is never called (step 0 throws before saving)
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order with zero loyalty points skips award — compensation still works cleanly")
        void awardPointsFails_zeroPoints_noPointsCalls() {
            Long orderId = 5L;
            Order pendingOrder = Order.builder()
                    .id(orderId).status(OrderStatus.PENDING)
                    .loyaltyPoints(0).userId(1L)   // earns nothing
                    .price(BigDecimal.valueOf(50))
                    .build();

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // succeeds because AwardPointsStep skips when loyaltyPoints == 0
            payOrchestrator.payOrder(orderId);

            verify(userServiceGateway, never()).updateLoyaltyPointsStrict(any(), any());
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, atLeast(1)).save(captor.capture());
            assertThat(captor.getAllValues().stream()
                    .anyMatch(o -> o.getStatus() == OrderStatus.PAID)).isTrue();
        }
    }
}
