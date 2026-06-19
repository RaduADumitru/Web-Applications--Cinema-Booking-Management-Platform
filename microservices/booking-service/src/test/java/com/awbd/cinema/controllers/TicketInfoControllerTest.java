package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.TicketInfoDTOs.SaveTicketInfoDTO;
import com.awbd.cinema.DTOs.TicketInfoDTOs.TicketInfoDTO;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.services.TicketInfoService.TicketInfoService;
import com.awbd.cinema.utils.RestPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketInfoController.class)
class TicketInfoControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TicketInfoService ticketInfoService;

    private TicketInfoDTO createSampleTicketInfoDTO() {
        return new TicketInfoDTO(1L, TicketType.ADULT, BigDecimal.valueOf(35.00));
    }

    // ==========================================
    // POST /ticket-info (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("POST /ticket-info")
    class CreateTicketInfo {

        @Test
        @DisplayName("Should return 201 Created when executed by STAFF with valid payload")
        void createTicketInfo_StaffAndValidPayload_ReturnsCreated() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveTicketInfoDTO dto = new SaveTicketInfoDTO(TicketType.ADULT, BigDecimal.valueOf(35.00));
            TicketInfoDTO responseDto = createSampleTicketInfoDTO();

            when(ticketInfoService.createTicketInfo(any(SaveTicketInfoDTO.class))).thenReturn(responseDto);

            mockMvc.perform(post("/ticket-info")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.type").value("ADULT"))
                    .andExpect(jsonPath("$.price").value(35.00));
        }

        @Test
        @DisplayName("Should return 422 Unprocessable Content when price validation fails")
        void createTicketInfo_InvalidPayload_ReturnsUnprocessableContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveTicketInfoDTO dto = new SaveTicketInfoDTO(TicketType.ADULT, BigDecimal.valueOf(-5.00));

            mockMvc.perform(post("/ticket-info")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when executed by a standard USER")
        void createTicketInfo_AdultUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser(); // Authenticated as Role.USER
            SaveTicketInfoDTO dto = new SaveTicketInfoDTO(TicketType.ADULT, BigDecimal.valueOf(35.00));

            mockMvc.perform(post("/ticket-info")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // GET /ticket-info & GET /ticket-info/{id} (Public)
    // ==========================================
    @Nested
    @DisplayName("GET /ticket-info read operations")
    class ReadTicketInfo {

        @Test
        @DisplayName("GET /ticket-info should return list of configurations for any authenticated user")
        void getTicketInfos_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            TicketInfoDTO info = createSampleTicketInfoDTO();
            RestPage<TicketInfoDTO> page = new RestPage<>(new PageImpl<>(List.of(info)));
            when(ticketInfoService.getTicketInfos()).thenReturn(page);

            mockMvc.perform(get("/ticket-info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1L))
                    .andExpect(jsonPath("$.content[0].type").value("ADULT"));
        }

        @Test
        @DisplayName("GET /ticket-info/{id} should return single configuration element details")
        void getTicketInfo_ValidId_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            TicketInfoDTO info = createSampleTicketInfoDTO();
            when(ticketInfoService.getTicketInfo(1L)).thenReturn(info);

            mockMvc.perform(get("/ticket-info/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.price").value(35.00));
        }
    }

    // ==========================================
    // PUT /ticket-info/{id} (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("PUT /ticket-info/{id}")
    class UpdateTicketInfo {

        @Test
        @DisplayName("Should return 200 OK when updating configuration as STAFF user")
        void updateTicketInfo_StaffUser_ReturnsOk() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveTicketInfoDTO updateDto = new SaveTicketInfoDTO(TicketType.CHILD, BigDecimal.valueOf(50.00));
            TicketInfoDTO responseDto = new TicketInfoDTO(1L, TicketType.CHILD, BigDecimal.valueOf(50.00));

            when(ticketInfoService.updateTicketInfo(eq(1L), any(SaveTicketInfoDTO.class))).thenReturn(responseDto);

            mockMvc.perform(put("/ticket-info/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("CHILD"))
                    .andExpect(jsonPath("$.price").value(50.00));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when updating configuration as standard USER")
        void updateTicketInfo_AdultUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();
            SaveTicketInfoDTO updateDto = new SaveTicketInfoDTO(TicketType.CHILD, BigDecimal.valueOf(50.00));

            mockMvc.perform(put("/ticket-info/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // DELETE /ticket-info/{id} (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("DELETE /ticket-info/{id}")
    class DeleteTicketInfo {

        @Test
        @DisplayName("Should return 24 No Content when deleting record configuration as STAFF user")
        void deleteTicketInfo_StaffUser_ReturnsNoContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            doNothing().when(ticketInfoService).deleteTicketInfo(1L);

            mockMvc.perform(delete("/ticket-info/1")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when attempting configuration deletion as standard USER")
        void deleteTicketInfo_AdultUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(delete("/ticket-info/1")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }
}
