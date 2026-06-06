package com.awbd.cinema.DTOs.OrderDTOs;

import com.awbd.cinema.entities.Order;
import com.awbd.cinema.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDTO(
        Long id,
        LocalDateTime createdAt,
        OrderStatus status,
        LocalDateTime paymentAt,
        LocalDateTime deletedAt,
        BigDecimal price,
        Integer loyaltyPoints,
        Integer pointsUsed,
        BigDecimal discount,
        Long userId,
        List<Long> ticketIds,
        Integer offerPercent,
        String offerMessage
) {
    public static OrderDTO from(Order o) {
        List<Long> ticketIds = o.getTickets() == null ? List.of() :
                o.getTickets().stream().map(t -> t.getId()).toList();
        Integer pointsUsed = o.getPointsSpend() != null ? o.getPointsSpend().getPointsUsed() : null;
        BigDecimal discount = o.getPointsSpend() != null ? o.getPointsSpend().getDiscount() : null;
        Integer offerPercent = o.getOffer() != null ? o.getOffer().getPercent() : null;
        String offerMessage = o.getOffer() != null
                ? "A " + o.getOffer().getPercent() + "% discount was applied for placing your order on " + o.getOffer().getDay() + "!"
                : null;
        return new OrderDTO(
                o.getId(),
                o.getCreatedAt(),
                o.getStatus(),
                o.getPaymentAt(),
                o.getDeletedAt(),
                o.getPrice(),
                o.getLoyaltyPoints(),
                pointsUsed,
                discount,
                o.getUser().getId(),
                ticketIds,
                offerPercent,
                offerMessage
        );
    }
}
