package com.awbd.cinema.services.SeatService;

import com.awbd.cinema.DTOs.SeatDTOs.GenerateSeatsDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SaveSeatDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SeatDTO;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.enums.RoomType;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.SeatCategoryRepository;
import com.awbd.cinema.repositories.SeatRepository;
import com.awbd.cinema.utils.RestPage;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    @CacheEvict(value = "seat_lists", allEntries = true)
    public SeatDTO createSeat(SaveSeatDTO dto) {
        Room room = roomRepository.findById(dto.roomId())
                .orElseThrow(() -> new NotFoundException("Room not found."));
        Seat seat = Seat.builder()
                .row(dto.row())
                .number(dto.number())
                .zone(dto.zone())
                .category(resolveCategory(dto.categoryId()))
                .build();
        Seat savedSeat = seatRepository.save(seat);
        room.getSeats().add(savedSeat);
        roomRepository.save(room);
        return SeatDTO.from(savedSeat);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "seat_lists", allEntries = true),
            @CacheEvict(value = "single_rooms", key = "#dto.roomId()"),
            @CacheEvict(value = "room_lists", allEntries = true)
    })
    public List<SeatDTO> generateSeats(GenerateSeatsDTO dto) {
        Room room = roomRepository.findById(dto.roomId())
                .orElseThrow(() -> new NotFoundException("Room not found."));
        SeatCategory category = resolveCategory(dto.categoryId());

        List<Seat> seats = new ArrayList<>();
        for (int rowOffset = 0; rowOffset < dto.rows(); rowOffset++) {
            int row = dto.startRow() + rowOffset;
            for (int seatOffset = 0; seatOffset < dto.seatsPerRow(); seatOffset++) {
                seats.add(Seat.builder()
                        .row(row)
                        .number(dto.startSeatNumber() + seatOffset)
                        .zone(dto.zone())
                        .category(category)
                        .build());
            }
        }

        List<Seat> savedSeats = seatRepository.saveAll(seats);
        room.getSeats().addAll(savedSeats);
        roomRepository.save(room);

        return savedSeats.stream()
                .map(SeatDTO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "seat_lists")
    public RestPage<SeatDTO> getSeats(String roomType, Long screenSessionId, Long movieId, Pageable pageable) {
        Specification<Seat> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            boolean needsRoomJoin = roomType != null || screenSessionId != null || movieId != null;
            boolean needsSessionJoin = screenSessionId != null || movieId != null;

            Join<Seat, Room> roomJoin = needsRoomJoin
                    ? root.join("rooms", JoinType.INNER) : null;
            Join<Room, ScreenSession> sessionJoin = needsSessionJoin
                    ? roomJoin.join("screenSessions", JoinType.INNER) : null;

            if (roomType != null) {
                predicates.add(cb.equal(roomJoin.get("type"), RoomType.valueOf(roomType.toUpperCase())));
            }
            if (screenSessionId != null) {
                predicates.add(cb.equal(sessionJoin.get("id"), screenSessionId));
            }
            if (movieId != null) {
                predicates.add(cb.equal(sessionJoin.get("movie").get("id"), movieId));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return new RestPage<>(seatRepository.findAll(spec, pageable).map(SeatDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "single_seat", key = "#id")
    public SeatDTO getSeat(Long id) {
        return seatRepository.findById(id)
                .map(SeatDTO::from)
                .orElseThrow(() -> new NotFoundException("Seat not found."));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_seat", key = "#id"),
            @CacheEvict(value = "seat_lists", allEntries = true)
    })
    public SeatDTO updateSeat(Long id, SaveSeatDTO dto) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Seat not found."));
        seat.setRow(dto.row());
        seat.setNumber(dto.number());
        seat.setZone(dto.zone());
        seat.setCategory(resolveCategory(dto.categoryId()));
        return SeatDTO.from(seatRepository.save(seat));
    }

    private SeatCategory resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return seatCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Seat category not found."));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_seat", key = "#id"),
            @CacheEvict(value = "seat_lists", allEntries = true)
    })
    public void deleteSeat(Long id) {
        if (!seatRepository.existsById(id)) {
            throw new NotFoundException("Seat not found.");
        }
        seatRepository.deleteById(id);
    }
}
