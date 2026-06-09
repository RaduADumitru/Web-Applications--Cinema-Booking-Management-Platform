package com.awbd.cinema.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Optional;

import com.awbd.cinema.DTOs.OfferDTOs.OfferDTO;
import com.awbd.cinema.DTOs.OfferDTOs.SaveOfferDTO;
import com.awbd.cinema.entities.Offer;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.OfferRepository;
import com.awbd.cinema.services.OfferService.OfferServiceImpl;
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

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock
    private OfferRepository offerRepository;

    @InjectMocks
    private OfferServiceImpl offerService;

    private Offer existingOffer;
    private SaveOfferDTO saveOfferDTO;

    @BeforeEach
    void setUp() {
        existingOffer = Offer.builder()
                .id(1L)
                .day(DayOfWeek.MONDAY)
                .percent(15)
                .build();

        saveOfferDTO = new SaveOfferDTO(DayOfWeek.MONDAY, 15);
    }

    @Nested
    @DisplayName("Create Offer Tests")
    class CreateOfferTests {

        @Test
        @DisplayName("Should successfully create an offer when day does not conflict")
        void createOffer_Success() {
            // Given
            when(offerRepository.existsByDay(saveOfferDTO.day())).thenReturn(false);
            when(offerRepository.save(any(Offer.class))).thenReturn(existingOffer);

            // When
            OfferDTO result = offerService.createOffer(saveOfferDTO);

            // Then
            assertNotNull(result);
            assertEquals(existingOffer.getId(), result.id());
            assertEquals(existingOffer.getDay(), result.day());
            assertEquals(existingOffer.getPercent(), result.percent());
            verify(offerRepository, times(1)).existsByDay(saveOfferDTO.day());
            verify(offerRepository, times(1)).save(any(Offer.class));
        }

        @Test
        @DisplayName("Should throw AlreadyExistsException when an offer for the day already exists")
        void createOffer_ThrowsAlreadyExistsException() {
            // Given
            when(offerRepository.existsByDay(saveOfferDTO.day())).thenReturn(true);

            // When & Then
            assertThrows(AlreadyExistsException.class, () -> offerService.createOffer(saveOfferDTO));
            verify(offerRepository, times(1)).existsByDay(saveOfferDTO.day());
            verify(offerRepository, never()).save(any(Offer.class));
        }
    }

    @Nested
    @DisplayName("Get Offers Tests")
    class GetOffersTests {

        @Test
        @DisplayName("Should return a page of OfferDTOs")
        void getOffers_Success() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Offer> offerPage = new PageImpl<>(Collections.singletonList(existingOffer));
            when(offerRepository.findAll(pageable)).thenReturn(offerPage);

            // When
            Page<OfferDTO> result = offerService.getOffers(pageable);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(existingOffer.getId(), result.getContent().get(0).id());
            verify(offerRepository, times(1)).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("Get Single Offer Tests")
    class GetSingleOfferTests {

        @Test
        @DisplayName("Should return OfferDTO when valid ID is provided")
        void getOffer_Success() {
            // Given
            Long id = 1L;
            when(offerRepository.findById(id)).thenReturn(Optional.of(existingOffer));

            // When
            OfferDTO result = offerService.getOffer(id);

            // Then
            assertNotNull(result);
            assertEquals(id, result.id());
            verify(offerRepository, times(1)).findById(id);
        }

        @Test
        @DisplayName("Should throw NotFoundException when offer ID does not exist")
        void getOffer_ThrowsNotFoundException() {
            // Given
            Long id = 1L;
            when(offerRepository.findById(id)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () -> offerService.getOffer(id));
            verify(offerRepository, times(1)).findById(id);
        }
    }

    @Nested
    @DisplayName("Update Offer Tests")
    class UpdateOfferTests {

        @Test
        @DisplayName("Should successfully update offer when the day is unchanged")
        void updateOffer_SameDay_Success() {
            // Given
            Long id = 1L;
            SaveOfferDTO updateDTO = new SaveOfferDTO(DayOfWeek.MONDAY, 25); // Same day, new percent
            when(offerRepository.findById(id)).thenReturn(Optional.of(existingOffer));
            when(offerRepository.save(any(Offer.class))).thenReturn(existingOffer);

            // When
            OfferDTO result = offerService.updateOffer(id, updateDTO);

            // Then
            assertNotNull(result);
            assertEquals(updateDTO.percent(), existingOffer.getPercent());
            verify(offerRepository, times(1)).findById(id);
            verify(offerRepository, never()).existsByDay(any());
            verify(offerRepository, times(1)).save(existingOffer);
        }

        @Test
        @DisplayName("Should successfully update offer when the day changes to an available day")
        void updateOffer_NewDaySuccess() {
            // Given
            Long id = 1L;
            SaveOfferDTO updateDTO = new SaveOfferDTO(DayOfWeek.FRIDAY, 20); // Different day
            when(offerRepository.findById(id)).thenReturn(Optional.of(existingOffer));
            when(offerRepository.existsByDay(DayOfWeek.FRIDAY)).thenReturn(false);
            when(offerRepository.save(any(Offer.class))).thenReturn(existingOffer);

            // When
            OfferDTO result = offerService.updateOffer(id, updateDTO);

            // Then
            assertNotNull(result);
            assertEquals(DayOfWeek.FRIDAY, existingOffer.getDay());
            verify(offerRepository, times(1)).findById(id);
            verify(offerRepository, times(1)).existsByDay(DayOfWeek.FRIDAY);
            verify(offerRepository, times(1)).save(existingOffer);
        }

        @Test
        @DisplayName("Should throw AlreadyExistsException when updating to a day that is already taken")
        void updateOffer_NewDayConflict_ThrowsAlreadyExistsException() {
            // Given
            Long id = 1L;
            SaveOfferDTO updateDTO = new SaveOfferDTO(DayOfWeek.FRIDAY, 20);
            when(offerRepository.findById(id)).thenReturn(Optional.of(existingOffer));
            when(offerRepository.existsByDay(DayOfWeek.FRIDAY)).thenReturn(true);

            // When & Then
            assertThrows(AlreadyExistsException.class, () -> offerService.updateOffer(id, updateDTO));
            verify(offerRepository, times(1)).findById(id);
            verify(offerRepository, times(1)).existsByDay(DayOfWeek.FRIDAY);
            verify(offerRepository, never()).save(any(Offer.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when trying to update a non-existent offer")
        void updateOffer_NotFound_ThrowsNotFoundException() {
            // Given
            Long id = 99L;
            when(offerRepository.findById(id)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () -> offerService.updateOffer(id, saveOfferDTO));
            verify(offerRepository, times(1)).findById(id);
            verify(offerRepository, never()).save(any(Offer.class));
        }
    }

    @Nested
    @DisplayName("Delete Offer Tests")
    class DeleteOfferTests {

        @Test
        @DisplayName("Should successfully delete offer when ID exists")
        void deleteOffer_Success() {
            // Given
            Long id = 1L;
            when(offerRepository.existsById(id)).thenReturn(true);

            // When
            offerService.deleteOffer(id);

            // Then
            verify(offerRepository, times(1)).existsById(id);
            verify(offerRepository, times(1)).deleteById(id);
        }

        @Test
        @DisplayName("Should throw NotFoundException when trying to delete an offer that does not exist")
        void deleteOffer_NotFound_ThrowsNotFoundException() {
            // Given
            Long id = 99L;
            when(offerRepository.existsById(id)).thenReturn(false);

            // When & Then
            assertThrows(NotFoundException.class, () -> offerService.deleteOffer(id));
            verify(offerRepository, times(1)).existsById(id);
            verify(offerRepository, never()).deleteById(anyLong());
        }
    }
}