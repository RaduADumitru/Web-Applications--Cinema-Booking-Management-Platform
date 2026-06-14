package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.clients.CatalogServiceClient;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.TicketInfoRepository;
import com.awbd.cinema.repositories.TicketRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketInfoRepository ticketInfoRepository;
    @Mock private CatalogServiceClient catalogServiceClient;

    @InjectMocks private TicketServiceImpl ticketService;

    private Ticket sampleTicket;
    private TicketInfo sampleTicketInfo;
    private TicketSetupDTO sampleSetup;

    @BeforeEach
    void setUp() {
        sampleTicketInfo = TicketInfo.builder()
                .id(4L).type(TicketType.ADULT).price(BigDecimal.valueOf(12.50)).build();

        sampleTicket = Ticket.builder()
                .id(100L).isAvailable(true)
                .seatId(1L).roomId(2L).screenSessionId(3L)
                .build();

        sampleSetup = new TicketSetupDTO(5, 10, "A", BigDecimal.ZERO, 0,
                "Inception", LocalDate.of(2026, 7, 1), LocalTime.of(19, 30), 0);
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
        @DisplayName("Should create a ticket from the catalog snapshot")
        void createTicket_Success() {
            when(ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(1L, 2L, 3L)).thenReturn(false);
            when(catalogServiceClient.getTicketSetup(1L, 2L, 3L)).thenReturn(sampleSetup);
            when(ticketRepository.save(any(Ticket.class))).thenReturn(sampleTicket);

            TicketDTO result = ticketService.createTicket(saveDto);

            assertNotNull(result);
            assertEquals(100L, result.id());
            assertTrue(result.isAvailable());
            verify(catalogServiceClient).getTicketSetup(1L, 2L, 3L);
            verify(ticketRepository, times(1)).save(any(Ticket.class));
        }

        @Test
        @DisplayName("Should throw AlreadyExistsException and skip the catalog call when ticket exists")
        void createTicket_AlreadyExists() {
            when(ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(1L, 2L, 3L)).thenReturn(true);

            assertThrows(AlreadyExistsException.class, () -> ticketService.createTicket(saveDto));
            verify(catalogServiceClient, never()).getTicketSetup(any(), any(), any());
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should propagate a clear error when catalog-service is unavailable")
        void createTicket_CatalogUnavailable() {
            when(ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(1L, 2L, 3L)).thenReturn(false);
            when(catalogServiceClient.getTicketSetup(1L, 2L, 3L))
                    .thenThrow(new BadRequestException("Catalog service is currently unavailable. Please try again later."));

            assertThrows(BadRequestException.class, () -> ticketService.createTicket(saveDto));
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
        void getTickets_AllFilters() {
            when(ticketRepository.findByScreenSessionIdAndRoomIdAndIsAvailable(3L, 2L, true, pageable)).thenReturn(ticketPage);
            Page<TicketDTO> result = ticketService.getTickets(3L, 2L, true, pageable);
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(ticketRepository).findByScreenSessionIdAndRoomIdAndIsAvailable(3L, 2L, true, pageable);
        }

        @Test
        void getTickets_SessionAndRoom() {
            when(ticketRepository.findByScreenSessionIdAndRoomId(3L, 2L, pageable)).thenReturn(ticketPage);
            Page<TicketDTO> result = ticketService.getTickets(3L, 2L, null, pageable);
            assertNotNull(result);
            verify(ticketRepository).findByScreenSessionIdAndRoomId(3L, 2L, pageable);
        }

        @Test
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
        void getTicket_Success() {
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(sampleTicket));
            TicketDTO result = ticketService.getTicket(100L);
            assertNotNull(result);
            assertEquals(100L, result.id());
        }

        @Test
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
        void bookTicket_AlreadyBooked() {
            sampleTicket.setAvailable(false);
            when(ticketRepository.findByIdForBooking(100L)).thenReturn(Optional.of(sampleTicket));
            assertThrows(BadRequestException.class, () -> ticketService.bookTicket(100L, bookDto));
            verify(ticketRepository, never()).save(any());
        }

        @Test
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
        void deleteTicket_Success() {
            when(ticketRepository.existsById(100L)).thenReturn(true);
            doNothing().when(ticketRepository).deleteById(100L);
            assertDoesNotThrow(() -> ticketService.deleteTicket(100L));
            verify(ticketRepository, times(1)).deleteById(100L);
        }

        @Test
        void deleteTicket_NotFound() {
            when(ticketRepository.existsById(100L)).thenReturn(false);
            assertThrows(NotFoundException.class, () -> ticketService.deleteTicket(100L));
            verify(ticketRepository, never()).deleteById(any());
        }
    }
}
