package com.awbd.cinema.services.RoomService;

import com.awbd.cinema.DTOs.RoomDTOs.RoomDTO;
import com.awbd.cinema.DTOs.RoomDTOs.SaveRoomDTO;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final ScreenSessionRepository screenSessionRepository;

    @Transactional
    public RoomDTO createRoom(SaveRoomDTO dto) {
        Room room = Room.builder()
                .type(dto.type())
                .name(dto.name())
                .floor(dto.floor())
                .build();
        return RoomDTO.from(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public Page<RoomDTO> getRooms(Pageable pageable) {
        return roomRepository.findAll(pageable).map(RoomDTO::from);
    }

    @Transactional(readOnly = true)
    public RoomDTO getRoom(Long id) {
        return roomRepository.findById(id)
                .map(RoomDTO::from)
                .orElseThrow(() -> new NotFoundException("Room not found."));
    }

    @Transactional
    public RoomDTO updateRoom(Long id, SaveRoomDTO dto) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Room not found."));
        room.setType(dto.type());
        room.setName(dto.name());
        room.setFloor(dto.floor());
        return RoomDTO.from(roomRepository.save(room));
    }

    @Transactional
    public void deleteRoom(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new NotFoundException("Room not found.");
        }
        roomRepository.deleteById(id);
    }

    @Transactional
    public RoomDTO addSeat(Long roomId, Long seatId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found."));
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException("Seat not found."));
        if (room.getSeats().stream().anyMatch(s -> s.getId().equals(seatId))) {
            throw new BadRequestException("Seat is already in this room.");
        }
        room.getSeats().add(seat);
        return RoomDTO.from(roomRepository.save(room));
    }

    @Transactional
    public RoomDTO addScreenSession(Long roomId, Long sessionId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found."));
        ScreenSession session = screenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Screen session not found."));
        if (room.getScreenSessions().stream().anyMatch(s -> s.getId().equals(sessionId))) {
            throw new BadRequestException("Screen session is already in this room.");
        }
        room.getScreenSessions().add(session);
        return RoomDTO.from(roomRepository.save(room));
    }

    @Transactional
    public RoomDTO removeSeat(Long roomId, Long seatId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found."));
        room.getSeats().removeIf(s -> s.getId().equals(seatId));
        return RoomDTO.from(roomRepository.save(room));
    }

    @Transactional
    public RoomDTO removeScreenSession(Long roomId, Long sessionId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found."));
        room.getScreenSessions().removeIf(s -> s.getId().equals(sessionId));
        return RoomDTO.from(roomRepository.save(room));
    }
}
