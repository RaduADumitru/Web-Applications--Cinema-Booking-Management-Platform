package com.awbd.cinema.sagas.payorder.steps;

import com.awbd.cinema.entities.Order;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.sagas.SagaStep;
import com.awbd.cinema.sagas.payorder.PayOrderContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarkOrderPaidStep implements SagaStep<PayOrderContext> {

    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public void execute(PayOrderContext ctx) {
        Order order = orderRepository.findById(ctx.getOrderId())
                .orElseThrow(() -> new NotFoundException("Order not found."));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Only PENDING orders can be paid.");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaymentAt(LocalDateTime.now());
        ctx.setOrder(orderRepository.save(order));
        ctx.setOrderMarkedPaid(true);
        log.info("Order {} marked as PAID.", ctx.getOrderId());
    }

    @Override
    @Transactional
    public void compensate(PayOrderContext ctx) {
        if (!ctx.isOrderMarkedPaid() || ctx.getOrder() == null) return;
        Order order = orderRepository.findById(ctx.getOrder().getId()).orElse(null);
        if (order == null) return;
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentAt(null);
        orderRepository.save(order);
        log.info("Order {} reverted to PENDING during compensation.", ctx.getOrderId());
    }
}
