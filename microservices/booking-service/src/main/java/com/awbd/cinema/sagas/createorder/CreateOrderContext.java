package com.awbd.cinema.sagas.createorder;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.PointsSpend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderContext {
    private UUID sagaId;
    private Long userId;
    private CreateOrderDTO dto;

    // Populated by DeductPointsStep
    private int pointsBefore;
    private boolean pointsDeducted;
    private PointsSpend pointsSpend;
    private BigDecimal pointsDiscount;

    // Populated by PersistOrderStep
    private Order savedOrder;
}
