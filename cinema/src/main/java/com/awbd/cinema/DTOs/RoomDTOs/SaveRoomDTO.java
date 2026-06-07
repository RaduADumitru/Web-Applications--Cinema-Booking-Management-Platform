package com.awbd.cinema.DTOs.RoomDTOs;

import com.awbd.cinema.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveRoomDTO(
        @NotNull(message = "The room type is required.") RoomType type,
        @NotBlank(message = "The room name is required.") String name,
        @NotNull(message = "The floor is required.") Integer floor
) {}
