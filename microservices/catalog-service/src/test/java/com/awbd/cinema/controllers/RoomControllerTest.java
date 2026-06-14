package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.RoomDTOs.RoomDTO;
import com.awbd.cinema.DTOs.RoomDTOs.SaveRoomDTO;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.enums.RoomType;
import com.awbd.cinema.services.RoomService.RoomService;
import com.awbd.cinema.utils.RestPage;
import tools.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
class RoomControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private RoomService roomService;

    private RoomDTO createSampleRoomDTO() {
        return new RoomDTO(1L, RoomType.IMAX, "Grand Arena", 2);
    }

    // ==========================================
    // ROOM CRUD ENDPOINTS
    // ==========================================
    @Nested
    @DisplayName("Room CRUD Operations")
    class RoomCrudOperations {

        @Test
        @DisplayName("POST /rooms should return 201 Created for STAFF with valid payload")
        void createRoom_StaffAndValidPayload_ReturnsCreated() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveRoomDTO dto = new SaveRoomDTO(RoomType.IMAX, "Grand Arena", 2);
            RoomDTO responseDto = createSampleRoomDTO();

            when(roomService.createRoom(any(SaveRoomDTO.class))).thenReturn(responseDto);

            mockMvc.perform(post("/rooms")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.name").value("Grand Arena"));
        }

        @Test
        @DisplayName("POST /rooms should return 422 Unprocessable Content when name validation fails")
        void createRoom_InvalidPayload_ReturnsUnprocessableContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveRoomDTO dto = new SaveRoomDTO(RoomType.IMAX, "", 2); // Blank name fails @NotBlank

            mockMvc.perform(post("/rooms")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        @DisplayName("POST /rooms should return 403 Forbidden for a standard USER")
        void createRoom_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();
            SaveRoomDTO dto = new SaveRoomDTO(RoomType.IMAX, "Grand Arena", 2);

            mockMvc.perform(post("/rooms")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /rooms should return a paginated collection for any logged-in user")
        void getRooms_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            RoomDTO roomDto = createSampleRoomDTO();
            when(roomService.getRooms(any(Pageable.class))).thenReturn(new RestPage<>(new PageImpl<>(List.of(roomDto))));

            mockMvc.perform(get("/rooms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Grand Arena"));
        }

        @Test
        @DisplayName("GET /rooms/{id} should return single element details")
        void getRoom_ValidId_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            RoomDTO roomDto = createSampleRoomDTO();
            when(roomService.getRoom(1L)).thenReturn(roomDto);

            mockMvc.perform(get("/rooms/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.floor").value(2));
        }

        @Test
        @DisplayName("PUT /rooms/{id} should update room details if user is STAFF")
        void updateRoom_StaffUser_ReturnsOk() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveRoomDTO updateDto = new SaveRoomDTO(RoomType.IMAX, "IMAX Screen 1", 1);
            RoomDTO responseDto = new RoomDTO(1L, RoomType.IMAX, "IMAX Screen 1", 1);

            when(roomService.updateRoom(eq(1L), any(SaveRoomDTO.class))).thenReturn(responseDto);

            mockMvc.perform(put("/rooms/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("IMAX"))
                    .andExpect(jsonPath("$.name").value("IMAX Screen 1"));
        }

        @Test
        @DisplayName("DELETE /rooms/{id} should return 204 No Content when completed by STAFF")
        void deleteRoom_StaffUser_ReturnsNoContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            doNothing().when(roomService).deleteRoom(1L);

            mockMvc.perform(delete("/rooms/1")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }

    // ==========================================
    // SEAT RELATIONSHIP ENDPOINTS
    // ==========================================
    @Nested
    @DisplayName("Seat Association Sub-resources")
    class SeatAssociationOperations {

        @Test
        @DisplayName("POST /rooms/{roomId}/seats/{seatId} should add seat if user is STAFF")
        void addSeat_StaffUser_ReturnsOk() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            RoomDTO responseDto = createSampleRoomDTO();
            when(roomService.addSeat(1L, 50L)).thenReturn(responseDto);

            mockMvc.perform(post("/rooms/1/seats/50")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L));
        }

        @Test
        @DisplayName("DELETE /rooms/{roomId}/seats/{seatId} should drop seat relation if user is STAFF")
        void removeSeat_StaffUser_ReturnsNoContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            when(roomService.removeSeat(1L, 50L)).thenReturn(createSampleRoomDTO());

            mockMvc.perform(delete("/rooms/1/seats/50")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("POST /rooms/{roomId}/seats/{seatId} should return 403 Forbidden for basic USER role")
        void addSeat_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(post("/rooms/1/seats/50")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // SCREEN SESSION RELATIONSHIP ENDPOINTS
    // ==========================================
    @Nested
    @DisplayName("Screen Session Association Sub-resources")
    class ScreenSessionAssociationOperations {

        @Test
        @DisplayName("POST /rooms/{roomId}/screen-sessions/{sessionId} should link entity if user is STAFF")
        void addScreenSession_StaffUser_ReturnsOk() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            RoomDTO responseDto = createSampleRoomDTO();
            when(roomService.addScreenSession(1L, 200L)).thenReturn(responseDto);

            mockMvc.perform(post("/rooms/1/screen-sessions/200")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L));
        }

        @Test
        @DisplayName("DELETE /rooms/{roomId}/screen-sessions/{sessionId} should unlink entity if user is STAFF")
        void removeScreenSession_StaffUser_ReturnsNoContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            when(roomService.removeScreenSession(1L, 200L)).thenReturn(createSampleRoomDTO());

            mockMvc.perform(delete("/rooms/1/screen-sessions/200")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("DELETE /rooms/{roomId}/screen-sessions/{sessionId} should return 403 Forbidden for basic USER role")
        void removeScreenSession_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(delete("/rooms/1/screen-sessions/200")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }
}
