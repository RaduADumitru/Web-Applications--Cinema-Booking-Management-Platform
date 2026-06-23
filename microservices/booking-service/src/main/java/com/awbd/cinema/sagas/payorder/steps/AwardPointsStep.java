package com.awbd.cinema.sagas.payorder.steps;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceGateway;
import com.awbd.cinema.sagas.SagaStep;
import com.awbd.cinema.sagas.payorder.PayOrderContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AwardPointsStep implements SagaStep<PayOrderContext> {

    private final UserServiceGateway userServiceGateway;

    @Override
    public void execute(PayOrderContext ctx) {
        int earned = ctx.getOrder().getLoyaltyPoints();
        if (earned <= 0) {
            ctx.setPointsAwarded(false);
            return;
        }

        int current = userServiceGateway.getLoyaltyPoints(ctx.getOrder().getUserId()).loyaltyPoints();
        ctx.setPointsBeforeAward(current);

        userServiceGateway.updateLoyaltyPointsStrict(ctx.getOrder().getUserId(),
                new AdjustLoyaltyPointsDTO(current + earned));
        ctx.setPointsAwarded(true);

        log.info("Awarded {} points to user {}. New balance: {}.",
                earned, ctx.getOrder().getUserId(), current + earned);
    }

    @Override
    public void compensate(PayOrderContext ctx) {
        if (!ctx.isPointsAwarded()) return;
        try {
            userServiceGateway.updateLoyaltyPointsStrict(ctx.getOrder().getUserId(),
                    new AdjustLoyaltyPointsDTO(ctx.getPointsBeforeAward()));
            log.info("Reverted points award for user {} to {}.",
                    ctx.getOrder().getUserId(), ctx.getPointsBeforeAward());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to revert points for user {} — manual reconciliation required.",
                    ctx.getOrder().getUserId(), e);
        }
    }
}
