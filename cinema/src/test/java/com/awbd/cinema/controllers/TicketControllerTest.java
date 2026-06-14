package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.services.TicketService.TicketService;
import com.awbd.cinema.utils.RestPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
class TicketControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TicketService ticketService;

    private TicketDTO sampleTicketDto;

    @BeforeEach
    void setUp() {
        sampleTicketDto = new TicketDTO(
                100L,
                true,
                1L,
                2L,
                3L,
                TicketType.ADULT,
                BigDecimal.valueOf(12.50)
        );
    }

    @Nested
    @DisplayName("POST /tickets (Create Ticket)")
    class CreateTicketTests {

        @Test
        @DisplayName("Should allow access and create ticket when user is STAFF")
        void createTicket_AsStaff_ReturnsCreated() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveTicketDTO dto = new SaveTicketDTO(1L, 2L, 3L);

            when(ticketService.createTicket(any(SaveTicketDTO.class))).thenReturn(sampleTicketDto);

            mockMvc.perform(post("/tickets")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.isAvailable").value(true));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when user is regular USER")
        void createTicket_AsUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();
            SaveTicketDTO dto = new SaveTicketDTO(1L, 2L, 3L);

            mockMvc.perform(post("/tickets")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 422 Unprocessable Content when input validation fails")
        void createTicket_InvalidDto_ReturnsUnprocessableContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveTicketDTO invalidDto = new SaveTicketDTO(null, null, null);

            mockMvc.perform(post("/tickets")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDto)))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    @DisplayName("GET /tickets (Get Paginated Tickets)")
    class GetTicketsTests {

        @Test
        @DisplayName("Should return paginated tickets list successfully")
        void getTickets_ReturnsPagedData() throws Exception {
            loginAsDefaultUser();
            RestPage<TicketDTO> ticketPage = new RestPage<>(new PageImpl<>(Collections.singletonList(sampleTicketDto)));

            when(ticketService.getTickets(eq(3L), eq(2L), eq(true), any(Pageable.class)))
                    .thenReturn(ticketPage);

            mockMvc.perform(get("/tickets")
                            .param("sessionId", "3")
                            .param("roomId", "2")
                            .param("isAvailable", "true")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(100L))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("GET /tickets/{id} (Get Single Ticket)")
    class GetTicketTests {

        @Test
        @DisplayName("Should return ticket details for valid ID")
        void getTicket_ReturnsTicket() throws Exception {
            loginAsDefaultUser();
            when(ticketService.getTicket(100L)).thenReturn(sampleTicketDto);

            mockMvc.perform(get("/tickets/{id}", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.price").value(12.50));
        }
    }

    @Nested
    @DisplayName("PATCH /tickets/{id}/book (Book Ticket)")
    class BookTicketTests {

        @Test
        @DisplayName("Should modify and return updated ticket allocation on booking validation success")
        void bookTicket_ValidRequest_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            BookTicketDTO bookDto = new BookTicketDTO(TicketType.ADULT);

            TicketDTO updatedTicketDto = new TicketDTO(
                    100L, false, 1L, 2L, 3L, TicketType.ADULT, BigDecimal.valueOf(12.50)
            );

            when(ticketService.bookTicket(eq(100L), any(BookTicketDTO.class))).thenReturn(updatedTicketDto);

            mockMvc.perform(patch("/tickets/{id}/book", 100L)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bookDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isAvailable").value(false))
                    .andExpect(jsonPath("$.type").value("ADULT"));
        }
    }

    @Nested
    @DisplayName("DELETE /tickets/{id} (Delete Ticket)")
    class DeleteTicketTests {

        @Test
        @DisplayName("Should allow deletion when user is STAFF")
        void deleteTicket_AsStaff_ReturnsNoContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            doNothing().when(ticketService).deleteTicket(100L);

            mockMvc.perform(delete("/tickets/{id}", 100L)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should block deletion when user is regular USER")
        void deleteTicket_AsUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(delete("/tickets/{id}", 100L)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }
}