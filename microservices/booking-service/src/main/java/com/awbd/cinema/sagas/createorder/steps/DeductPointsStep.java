package com.awbd.cinema.sagas.createorder.steps;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceGateway;
import com.awbd.cinema.entities.PointsSpend;
import com.awbd.cinema.sagas.SagaStep;
import com.awbd.cinema.sagas.createorder.CreateOrderContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeductPointsStep implements SagaStep<CreateOrderContext> {

    private final UserServiceGateway userServiceGateway;

    @Override
    public void execute(CreateOrderContext ctx) {
        if (!ctx.getDto().useDiscount()) {
            ctx.setPointsDeducted(false);
            return;
        }

        int current = userServiceGateway.getLoyaltyPoints(ctx.getUserId()).loyaltyPoints();
        ctx.setPointsBefore(current);

        if (current <= 0) {
            ctx.setPointsDeducted(false);
            return;
        }

        BigDecimal discount = PointsSpend.calculateDiscount(current);
        ctx.setPointsSpend(PointsSpend.builder()
                .pointsUsed(current)
                .discount(discount)
                .build());
        ctx.setPointsDiscount(discount);

        userServiceGateway.updateLoyaltyPointsStrict(ctx.getUserId(), new AdjustLoyaltyPointsDTO(0));
        ctx.setPointsDeducted(true);

        log.info("Deducted {} points from user {} (discount: ${}).", current, ctx.getUserId(), discount);
    }

    @Override
    public void compensate(CreateOrderContext ctx) {
        if (!ctx.isPointsDeducted()) return;
        try {
            userServiceGateway.updateLoyaltyPointsStrict(ctx.getUserId(),
                    new AdjustLoyaltyPointsDTO(ctx.getPointsBefore()));
            log.info("Restored {} points to user {} during compensation.", ctx.getPointsBefore(), ctx.getUserId());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to restore {} points to user {} — manual reconciliation required.",
                    ctx.getPointsBefore(), ctx.getUserId(), e);
        }
    }
}
