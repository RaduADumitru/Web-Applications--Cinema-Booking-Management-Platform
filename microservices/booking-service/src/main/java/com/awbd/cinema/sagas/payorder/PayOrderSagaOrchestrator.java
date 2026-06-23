package com.awbd.cinema.sagas.payorder;

import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.enums.SagaType;
import com.awbd.cinema.repositories.SagaInstanceRepository;
import com.awbd.cinema.sagas.SagaOrchestrator;
import com.awbd.cinema.sagas.SagaStep;
import com.awbd.cinema.sagas.payorder.steps.AwardPointsStep;
import com.awbd.cinema.sagas.payorder.steps.MarkOrderPaidStep;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PayOrderSagaOrchestrator extends SagaOrchestrator<PayOrderContext, OrderDTO> {

    private final MarkOrderPaidStep markOrderPaidStep;
    private final AwardPointsStep awardPointsStep;

    public PayOrderSagaOrchestrator(SagaInstanceRepository sagaInstanceRepository,
                                    MarkOrderPaidStep markOrderPaidStep,
                                    AwardPointsStep awardPointsStep) {
        super(sagaInstanceRepository);
        this.markOrderPaidStep = markOrderPaidStep;
        this.awardPointsStep = awardPointsStep;
    }

    public OrderDTO payOrder(Long orderId) {
        return execute(PayOrderContext.builder()
                .sagaId(UUID.randomUUID())
                .orderId(orderId)
                .build());
    }

    @Override
    protected List<SagaStep<PayOrderContext>> steps() {
        return List.of(markOrderPaidStep, awardPointsStep);
    }

    @Override
    protected SagaType sagaType() {
        return SagaType.PAY_ORDER;
    }

    @Override
    protected UUID sagaId(PayOrderContext ctx) {
        return ctx.getSagaId();
    }

    @Override
    protected String serializePayload(PayOrderContext ctx) {
        return "{\"orderId\":" + ctx.getOrderId()
                + ",\"orderMarkedPaid\":" + ctx.isOrderMarkedPaid()
                + ",\"pointsBeforeAward\":" + ctx.getPointsBeforeAward()
                + ",\"pointsAwarded\":" + ctx.isPointsAwarded() + "}";
    }

    @Override
    protected OrderDTO result(PayOrderContext ctx) {
        return OrderDTO.from(ctx.getOrder());
    }
}
