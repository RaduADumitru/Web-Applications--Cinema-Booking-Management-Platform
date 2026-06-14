package com.awbd.cinema.DTOs.OrderDTOs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderDTO(
        @NotEmpty(message = "At least one ticket is required.")
        List<@Valid OrderItemDTO> items,

        boolean useDiscount
) {}
