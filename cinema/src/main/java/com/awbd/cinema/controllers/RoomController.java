package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.RoomDTOs.RoomDTO;
import com.awbd.cinema.DTOs.RoomDTOs.SaveRoomDTO;
import com.awbd.cinema.services.RoomService.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<RoomDTO> createRoom(@Valid @RequestBody SaveRoomDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(dto));
    }

    @GetMapping
    public ResponseEntity<List<RoomDTO>> getRooms() {
        return ResponseEntity.ok(roomService.getRooms());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomDTO> getRoom(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoom(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<RoomDTO> updateRoom(@PathVariable Long id, @Valid @RequestBody SaveRoomDTO dto) {
        return ResponseEntity.ok(roomService.updateRoom(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/seats/{seatId}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<RoomDTO> addSeat(@PathVariable Long roomId, @PathVariable Long seatId) {
        return ResponseEntity.ok(roomService.addSeat(roomId, seatId));
    }

    @PostMapping("/{roomId}/screen-sessions/{sessionId}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<RoomDTO> addScreenSession(@PathVariable Long roomId, @PathVariable Long sessionId) {
        return ResponseEntity.ok(roomService.addScreenSession(roomId, sessionId));
    }

    @DeleteMapping("/{roomId}/seats/{seatId}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> removeSeat(@PathVariable Long roomId, @PathVariable Long seatId) {
        roomService.removeSeat(roomId, seatId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}/screen-sessions/{sessionId}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> removeScreenSession(@PathVariable Long roomId, @PathVariable Long sessionId) {
        roomService.removeScreenSession(roomId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
