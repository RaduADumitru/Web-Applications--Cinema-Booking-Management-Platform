package com.awbd.cinema.sagas.createorder;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.enums.SagaType;
import com.awbd.cinema.repositories.SagaInstanceRepository;
import com.awbd.cinema.sagas.SagaOrchestrator;
import com.awbd.cinema.sagas.SagaStep;
import com.awbd.cinema.sagas.createorder.steps.DeductPointsStep;
import com.awbd.cinema.sagas.createorder.steps.PersistOrderStep;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CreateOrderSagaOrchestrator extends SagaOrchestrator<CreateOrderContext, OrderDTO> {

    private final DeductPointsStep deductPointsStep;
    private final PersistOrderStep persistOrderStep;

    public CreateOrderSagaOrchestrator(SagaInstanceRepository sagaInstanceRepository,
                                       DeductPointsStep deductPointsStep,
                                       PersistOrderStep persistOrderStep) {
        super(sagaInstanceRepository);
        this.deductPointsStep = deductPointsStep;
        this.persistOrderStep = persistOrderStep;
    }

    public OrderDTO createOrder(CreateOrderDTO dto, Long userId) {
        return execute(CreateOrderContext.builder()
                .sagaId(UUID.randomUUID())
                .userId(userId)
                .dto(dto)
                .build());
    }

    @Override
    protected List<SagaStep<CreateOrderContext>> steps() {
        return List.of(deductPointsStep, persistOrderStep);
    }

    @Override
    protected SagaType sagaType() {
        return SagaType.CREATE_ORDER;
    }

    @Override
    protected UUID sagaId(CreateOrderContext ctx) {
        return ctx.getSagaId();
    }

    @Override
    protected String serializePayload(CreateOrderContext ctx) {
        Long orderId = ctx.getSavedOrder() != null ? ctx.getSavedOrder().getId() : null;
        return "{\"userId\":" + ctx.getUserId()
                + ",\"pointsBefore\":" + ctx.getPointsBefore()
                + ",\"pointsDeducted\":" + ctx.isPointsDeducted()
                + ",\"orderId\":" + orderId + "}";
    }

    @Override
    protected OrderDTO result(CreateOrderContext ctx) {
        return OrderDTO.from(ctx.getSavedOrder());
    }
}
