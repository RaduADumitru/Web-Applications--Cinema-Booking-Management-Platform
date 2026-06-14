package com.awbd.cinema.DTOs.OrderDTOs;

import java.math.BigDecimal;

public record DiscountPreviewDTO(
        Integer loyaltyPoints,
        BigDecimal potentialDiscount
) {}
