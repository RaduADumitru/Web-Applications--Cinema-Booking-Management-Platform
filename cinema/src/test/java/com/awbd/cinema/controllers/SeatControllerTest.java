package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.SeatDTOs.SaveSeatDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SeatDTO;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.enums.SeatZone;
import com.awbd.cinema.services.SeatService.SeatService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SeatController.class)
class SeatControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SeatService seatService;

    private SeatDTO createSampleSeatDTO() {
        return new SeatDTO(1L, 3, 14, SeatZone.VIP, 5L);
    }

    // ==========================================
    // POST /seats (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("POST /seats")
    class CreateSeat {

        @Test
        @DisplayName("Should return 201 Created when executed by STAFF with valid payload")
        void createSeat_StaffAndValidPayload_ReturnsCreated() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveSeatDTO dto = new SaveSeatDTO(3, 14, SeatZone.VIP, 5L, 10L);
            SeatDTO responseDto = createSampleSeatDTO();

            when(seatService.createSeat(any(SaveSeatDTO.class))).thenReturn(responseDto);

            mockMvc.perform(post("/seats")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.row").value(3))
                    .andExpect(jsonPath("$.number").value(14))
                    .andExpect(jsonPath("$.zone").value("VIP"))
                    .andExpect(jsonPath("$.categoryId").value(5L));
        }

        @Test
        @DisplayName("Should return 422 Unprocessable Content when validation fails (negative row)")
        void createSeat_InvalidPayload_ReturnsUnprocessableContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            // Row cannot be negative or zero according to @Positive
            SaveSeatDTO dto = new SaveSeatDTO(-1, 14, SeatZone.VIP, 5L, 10L);

            mockMvc.perform(post("/seats")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when mandatory fields are missing")
        void createSeat_MissingRoomId_ReturnsUnprocessableContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            // Room ID is marked @NotNull
            SaveSeatDTO dto = new SaveSeatDTO(3, 14, SeatZone.VIP, 5L, null);

            mockMvc.perform(post("/seats")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when executed by a standard USER")
        void createSeat_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();
            SaveSeatDTO dto = new SaveSeatDTO(3, 14, SeatZone.VIP, 5L, 10L);

            mockMvc.perform(post("/seats")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // GET /seats (Public Read / Authenticated)
    // ==========================================
    @Nested
    @DisplayName("GET /seats specification filters")
    class ReadSeats {

        @Test
        @DisplayName("Should return 200 OK with paginated list when queried with specifications")
        void getSeats_WithFilters_ReturnsPaginatedSeats() throws Exception {
            loginAsDefaultUser();
            SeatDTO seatDto = createSampleSeatDTO();

            when(seatService.getSeats(eq("STANDARD"), eq(100L), eq(200L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(seatDto)));

            mockMvc.perform(get("/seats")
                            .param("roomType", "STANDARD")
                            .param("screenSessionId", "100")
                            .param("movieId", "200")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1L))
                    .andExpect(jsonPath("$.content[0].number").value(14));
        }

        @Test
        @DisplayName("GET /seats/{id} should return accurate element specification matching ID")
        void getSeat_ValidId_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            SeatDTO seatDto = createSampleSeatDTO();
            when(seatService.getSeat(1L)).thenReturn(seatDto);

            mockMvc.perform(get("/seats/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.zone").value("VIP"));
        }
    }

    // ==========================================
    // PUT /seats/{id} (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("PUT /seats/{id}")
    class UpdateSeat {

        @Test
        @DisplayName("Should return 200 OK and serialize response when updated by STAFF")
        void updateSeat_StaffUser_ReturnsOk() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveSeatDTO updateDto = new SaveSeatDTO(4, 15, SeatZone.A, null, 10L);
            SeatDTO responseDto = new SeatDTO(1L, 4, 15, SeatZone.A, null);

            when(seatService.updateSeat(eq(1L), any(SaveSeatDTO.class))).thenReturn(responseDto);

            mockMvc.perform(put("/seats/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.row").value(4))
                    .andExpect(jsonPath("$.number").value(15))
                    .andExpect(jsonPath("$.zone").value("A"))
                    .andExpect(jsonPath("$.categoryId").value((Object) null));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when trying to update configuration as standard USER")
        void updateSeat_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();
            SaveSeatDTO updateDto = new SaveSeatDTO(4, 15, SeatZone.A, null, 10L);

            mockMvc.perform(put("/seats/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // DELETE /seats/{id} (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("DELETE /seats/{id}")
    class DeleteSeat {

        @Test
        @DisplayName("Should return 24 No Content when deleted by STAFF user profile")
        void deleteSeat_StaffUser_ReturnsNoContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            doNothing().when(seatService).deleteSeat(1L);

            mockMvc.perform(delete("/seats/1")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when targeted deletion is run by standard USER")
        void deleteSeat_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(delete("/seats/1")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }
}
