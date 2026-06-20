package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.entities.*;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.services.TicketService.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ScreenSessionRepository screenSessionRepository;
    @Mock private TicketInfoRepository ticketInfoRepository;

    @InjectMocks private TicketServiceImpl ticketService;

    private Seat sampleSeat;
    private Room sampleRoom;
    private ScreenSession sampleSession;
    private Ticket sampleTicket;
    private TicketInfo sampleTicketInfo;

    @BeforeEach
    void setUp() {
        sampleSeat = Seat.builder().id(1L).row(5).number(10).build();
        sampleRoom = Room.builder().id(2L).name("IMAX 1").floor(1).build();
        sampleSession = ScreenSession.builder().id(3L).build();

        sampleTicketInfo = TicketInfo.builder()
                .id(4L)
                .type(TicketType.ADULT)
                .price(BigDecimal.valueOf(12.50))
                .build();

        sampleTicket = Ticket.builder()
                .id(100L)
                .isAvailable(true)
                .seat(sampleSeat)
                .room(sampleRoom)
                .screenSession(sampleSession)
                .build();
    }

    @Nested
    @DisplayName("Create Ticket Tests")
    class CreateTicketTests {

        private SaveTicketDTO saveDto;

        @BeforeEach
        void setup() {
            saveDto = new SaveTicketDTO(1L, 2L, 3L);
        }

        @Test
        @DisplayName("Should successfully create a ticket")
        void createTicket_Success() {
            when(seatRepository.findById(1L)).thenReturn(Optional.of(sampleSeat));
            when(roomRepository.findById(2L)).thenReturn(Optional.of(sampleRoom));
            when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(sampleSession));
            when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(true);
            when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(true);
            when(ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(1L, 2L, 3L)).thenReturn(false);
            when(ticketRepository.save(any(Ticket.class))).thenReturn(sampleTicket);

            TicketDTO result = ticketService.createTicket(saveDto);

            assertNotNull(result);
            assertEquals(100L, result.id());
            assertTrue(result.isAvailable());
            verify(ticketRepository, times(1)).save(any(Ticket.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when seat is not found")
        void createTicket_SeatNotFound() {
            when(seatRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> ticketService.createTicket(saveDto));
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when seat does not belong to room")
        void createTicket_SeatNotInRoom() {
            when(seatRepository.findById(1L)).thenReturn(Optional.of(sampleSeat));
            when(roomRepository.findById(2L)).thenReturn(Optional.of(sampleRoom));
            when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(sampleSession));
            when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(false);

            assertThrows(BadRequestException.class, () -> ticketService.createTicket(saveDto));
        }

        @Test
        @DisplayName("Should throw AlreadyExistsException when ticket already exists")
        void createTicket_AlreadyExists() {
            when(seatRepository.findById(1L)).thenReturn(Optional.of(sampleSeat));
            when(roomRepository.findById(2L)).thenReturn(Optional.of(sampleRoom));
            when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(sampleSession));
            when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(true);
            when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(true);
            when(ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(1L, 2L, 3L)).thenReturn(true);

            assertThrows(AlreadyExistsException.class, () -> ticketService.createTicket(saveDto));
        }

        @Test
        @DisplayName("Should throw NotFoundException when the session's movie is soft-deleted")
        void createTicket_SessionOfSoftDeletedMovie_ThrowsNotFound() {
            when(seatRepository.findById(1L)).thenReturn(Optional.of(sampleSeat));
            when(roomRepository.findById(2L)).thenReturn(Optional.of(sampleRoom));
            // findActiveById returns empty for a session whose movie is soft-deleted (hidden).
            when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> ticketService.createTicket(saveDto));
            verify(ticketRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Tickets (Filtered/Paged) Tests")
    class GetTicketsTests {

        private final Pageable pageable = PageRequest.of(0, 10);
        private Page<Ticket> ticketPage;

        @BeforeEach
        void setup() {
            ticketPage = new PageImpl<>(Collections.singletonList(sampleTicket));
        }

        @Test
        @DisplayName("Should filter by session, room, and availability when all parameters are provided")
        void getTickets_AllFilters() {
            when(ticketRepository.findByScreenSessionIdAndRoomIdAndIsAvailable(3L, 2L, true, pageable))
                    .thenReturn(ticketPage);

            Page<TicketDTO> result = ticketService.getTickets(3L, 2L, true, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(ticketRepository).findByScreenSessionIdAndRoomIdAndIsAvailable(3L, 2L, true, pageable);
        }

        @Test
        @DisplayName("Should filter by session and room when availability is null")
        void getTickets_SessionAndRoom() {
            when(ticketRepository.findByScreenSessionIdAndRoomId(3L, 2L, pageable)).thenReturn(ticketPage);

            Page<TicketDTO> result = ticketService.getTickets(3L, 2L, null, pageable);

            assertNotNull(result);
            verify(ticketRepository).findByScreenSessionIdAndRoomId(3L, 2L, pageable);
        }

        @Test
        @DisplayName("Should return all tickets when all filters are null")
        void getTickets_NoFilters() {
            when(ticketRepository.findAll(pageable)).thenReturn(ticketPage);

            Page<TicketDTO> result = ticketService.getTickets(null, null, null, pageable);

            assertNotNull(result);
            verify(ticketRepository).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("Get Single Ticket Tests")
    class GetTicketTests {

        @Test
        @DisplayName("Should return TicketDTO when ticket exists")
        void getTicket_Success() {
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(sampleTicket));

            TicketDTO result = ticketService.getTicket(100L);

            assertNotNull(result);
            assertEquals(100L, result.id());
        }

        @Test
        @DisplayName("Should throw NotFoundException when ticket does not exist")
        void getTicket_NotFound() {
            when(ticketRepository.findById(100L)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> ticketService.getTicket(100L));
        }
    }

    @Nested
    @DisplayName("Book Ticket Tests")
    class BookTicketTests {

        private BookTicketDTO bookDto;

        @BeforeEach
        void setup() {
            bookDto = new BookTicketDTO(TicketType.ADULT);
        }

        @Test
        @DisplayName("Should successfully book an available ticket")
        void bookTicket_Success() {
            when(ticketRepository.findByIdForBooking(100L)).thenReturn(Optional.of(sampleTicket));
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.of(sampleTicketInfo));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TicketDTO result = ticketService.bookTicket(100L, bookDto);

            assertNotNull(result);
            assertFalse(result.isAvailable());
            assertEquals(TicketType.ADULT, result.type());
            assertEquals(BigDecimal.valueOf(12.50), result.price());
            verify(ticketRepository).save(sampleTicket);
        }

        @Test
        @DisplayName("Should throw BadRequestException when ticket is already booked")
        void bookTicket_AlreadyBooked() {
            sampleTicket.setAvailable(false);
            when(ticketRepository.findByIdForBooking(100L)).thenReturn(Optional.of(sampleTicket));

            assertThrows(BadRequestException.class, () -> ticketService.bookTicket(100L, bookDto));
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when pricing configuration is missing")
        void bookTicket_TicketInfoNotFound() {
            when(ticketRepository.findByIdForBooking(100L)).thenReturn(Optional.of(sampleTicket));
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> ticketService.bookTicket(100L, bookDto));
        }
    }

    @Nested
    @DisplayName("Delete Ticket Tests")
    class DeleteTicketTests {

        @Test
        @DisplayName("Should delete ticket if it exists")
        void deleteTicket_Success() {
            when(ticketRepository.existsById(100L)).thenReturn(true);
            doNothing().when(ticketRepository).deleteById(100L);

            assertDoesNotThrow(() -> ticketService.deleteTicket(100L));
            verify(ticketRepository, times(1)).deleteById(100L);
        }

        @Test
        @DisplayName("Should throw NotFoundException when ticket to delete does not exist")
        void deleteTicket_NotFound() {
            when(ticketRepository.existsById(100L)).thenReturn(false);

            assertThrows(NotFoundException.class, () -> ticketService.deleteTicket(100L));
            verify(ticketRepository, never()).deleteById(any());
        }
    }
}
