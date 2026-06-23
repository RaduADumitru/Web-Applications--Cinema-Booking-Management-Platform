package com.awbd.cinema.sagas.payorder;

import com.awbd.cinema.entities.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOrderContext {
    private UUID sagaId;
    private Long orderId;

    // Populated by MarkOrderPaidStep
    private Order order;
    private boolean orderMarkedPaid;

    // Populated by AwardPointsStep
    private int pointsBeforeAward;
    private boolean pointsAwarded;
}
