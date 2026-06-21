package com.awbd.cinema.DTOs.SeatCategoryDTOs;

import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.enums.SeatCategoryType;

import java.math.BigDecimal;

public record SeatCategoryDTO(
        Long id,
        SeatCategoryType type,
        BigDecimal extraFee,
        Integer extraPoints
) {
    public static SeatCategoryDTO from(SeatCategory c) {
        return new SeatCategoryDTO(c.getId(), c.getType(), c.getExtraFee(), c.getExtraPoints());
    }
}
