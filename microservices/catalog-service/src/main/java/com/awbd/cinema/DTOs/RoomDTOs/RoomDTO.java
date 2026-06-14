package com.awbd.cinema.DTOs.RoomDTOs;

import com.awbd.cinema.entities.Room;
import com.awbd.cinema.enums.RoomType;

public record RoomDTO(
        Long id,
        RoomType type,
        String name,
        Integer floor
) {
    public static RoomDTO from(Room r) {
        return new RoomDTO(r.getId(), r.getType(), r.getName(), r.getFloor());
    }
}
