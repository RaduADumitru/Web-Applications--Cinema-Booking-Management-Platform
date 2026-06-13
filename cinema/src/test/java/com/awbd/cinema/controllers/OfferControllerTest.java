package com.awbd.cinema.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.DayOfWeek;
import java.util.Collections;

import com.awbd.cinema.DTOs.OfferDTOs.OfferDTO;
import com.awbd.cinema.DTOs.OfferDTOs.SaveOfferDTO;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.services.OfferService.OfferService;
import com.awbd.cinema.utils.RestPage;
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

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(OfferController.class)
class OfferControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OfferService offerService;

    private final OfferDTO mockOfferDTO = new OfferDTO(1L, DayOfWeek.MONDAY, 15);
    private final SaveOfferDTO validSaveOfferDTO = new SaveOfferDTO(DayOfWeek.MONDAY, 15);
    private final SaveOfferDTO invalidSaveOfferDTO = new SaveOfferDTO(null, 150);

    @Nested
    @DisplayName("POST /offers - Create Offer")
    class CreateOfferTests {

        @Test
        @DisplayName("Should create offer successfully when user is STAFF")
        void createOffer_Authorized() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            when(offerService.createOffer(any(SaveOfferDTO.class))).thenReturn(mockOfferDTO);

            mockMvc.perform(post("/offers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validSaveOfferDTO)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(mockOfferDTO.id()))
                    .andExpect(jsonPath("$.day").value(mockOfferDTO.day().toString()))
                    .andExpect(jsonPath("$.percent").value(mockOfferDTO.percent()));
        }

        @Test
        @DisplayName("Should return 422 Unprocessable Content when payload validation fails")
        void createOffer_ValidationError() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);

            mockMvc.perform(post("/offers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidSaveOfferDTO)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Validation failed."))
                    .andExpect(jsonPath("$.details.day").exists())
                    .andExpect(jsonPath("$.details.percent").exists());
        }

        @Test
        @DisplayName("Should return 409 Conflict when business exception AlreadyExistsException is thrown")
        void createOffer_AlreadyExists() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            when(offerService.createOffer(any(SaveOfferDTO.class)))
                    .thenThrow(new AlreadyExistsException("An offer for MONDAY already exists."));

            mockMvc.perform(post("/offers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validSaveOfferDTO)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("An offer for MONDAY already exists."));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when authenticated user is not STAFF")
        void createOffer_Forbidden() throws Exception {
            loginAsDefaultUser(); // Logs in as Role.USER

            mockMvc.perform(post("/offers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validSaveOfferDTO)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when user is anonymous")
        void createOffer_Unauthorized() throws Exception {
            // No login action called, context is clear

            mockMvc.perform(post("/offers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validSaveOfferDTO)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /offers - Get Paginated Offers")
    class GetOffersTests {

        @Test
        @DisplayName("Should return paginated offers publicly without authentication")
        void getOffers_PublicSuccess() throws Exception {
            loginAsDefaultUser();

            RestPage<OfferDTO> page = new RestPage<>(new PageImpl<>(Collections.singletonList(mockOfferDTO)));
            when(offerService.getOffers(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/offers")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(mockOfferDTO.id()))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("GET /offers/{id} - Get Single Offer")
    class GetSingleOfferTests {

        @Test
        @DisplayName("Should return offer details publicly without authentication")
        void getOffer_PublicSuccess() throws Exception {

            loginAsDefaultUser();
            when(offerService.getOffer(1L)).thenReturn(mockOfferDTO);

            mockMvc.perform(get("/offers/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(mockOfferDTO.id()))
                    .andExpect(jsonPath("$.day").value(mockOfferDTO.day().toString()));
        }

        @Test
        @DisplayName("Should return 404 Not Found when offer does not exist")
        void getOffer_NotFound() throws Exception {
            loginAsDefaultUser();
            when(offerService.getOffer(99L)).thenThrow(new NotFoundException("Offer not found with id: 99"));

            mockMvc.perform(get("/offers/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Offer not found with id: 99"));
        }
    }

    @Nested
    @DisplayName("PUT /offers/{id} - Update Offer")
    class UpdateOfferTests {

        @Test
        @DisplayName("Should update offer successfully when user is STAFF")
        void updateOffer_Authorized() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            when(offerService.updateOffer(eq(1L), any(SaveOfferDTO.class))).thenReturn(mockOfferDTO);

            mockMvc.perform(put("/offers/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validSaveOfferDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(mockOfferDTO.id()));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when user is not STAFF")
        void updateOffer_Forbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(put("/offers/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validSaveOfferDTO)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when user is anonymous")
        void updateOffer_Unauthorized() throws Exception {
            mockMvc.perform(put("/offers/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validSaveOfferDTO)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /offers/{id} - Delete Offer")
    class DeleteOfferTests {

        @Test
        @DisplayName("Should delete offer successfully when user is STAFF")
        void deleteOffer_Authorized() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            doNothing().when(offerService).deleteOffer(1L);

            mockMvc.perform(delete("/offers/1"))
                    .andExpect(status().isNoContent());

            verify(offerService, times(1)).deleteOffer(1L);
        }

        @Test
        @DisplayName("Should return 403 Forbidden when user trying to delete is not STAFF")
        void deleteOffer_Forbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(delete("/offers/1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when user attempting deletion is anonymous")
        void deleteOffer_Unauthorized() throws Exception {
            mockMvc.perform(delete("/offers/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}