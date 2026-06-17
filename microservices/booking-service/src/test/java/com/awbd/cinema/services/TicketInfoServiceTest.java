package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.TicketInfoDTOs.SaveTicketInfoDTO;
import com.awbd.cinema.DTOs.TicketInfoDTOs.TicketInfoDTO;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.TicketInfoRepository;
import com.awbd.cinema.services.TicketInfoService.TicketInfoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketInfoServiceTest {

    @Mock
    private TicketInfoRepository ticketInfoRepository;

    @InjectMocks
    private TicketInfoServiceImpl ticketInfoService;

    private TicketInfo sampleTicketInfo;
    private SaveTicketInfoDTO saveDto;

    @BeforeEach
    void setUp() {
        sampleTicketInfo = TicketInfo.builder()
                .id(1L)
                .type(TicketType.ADULT)
                .price(BigDecimal.valueOf(35.00))
                .build();

        saveDto = new SaveTicketInfoDTO(TicketType.ADULT, BigDecimal.valueOf(35.00));
    }

    // ==========================================
    // CREATE TICKET INFO TESTS
    // ==========================================
    @Nested
    @DisplayName("createTicketInfo Tests")
    class CreateTicketInfoTests {

        @Test
        @DisplayName("Should throw AlreadyExistsException when ticket type already exists")
        void createTicketInfo_TypeAlreadyExists_ThrowsAlreadyExistsException() {
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.of(sampleTicketInfo));

            assertThatThrownBy(() -> ticketInfoService.createTicketInfo(saveDto))
                    .isInstanceOf(AlreadyExistsException.class)
                    .hasMessageContaining("Ticket info for type 'ADULT' already exists.");

            verify(ticketInfoRepository, never()).save(any(TicketInfo.class));
        }

        @Test
        @DisplayName("Should successfully save and return TicketInfoDTO when type is unique")
        void createTicketInfo_Success_ReturnsTicketInfoDTO() {
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.empty());
            when(ticketInfoRepository.save(any(TicketInfo.class))).thenReturn(sampleTicketInfo);

            TicketInfoDTO result = ticketInfoService.createTicketInfo(saveDto);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.type()).isEqualTo(TicketType.ADULT);
            assertThat(result.price()).isEqualByComparingTo("35.00");
            verify(ticketInfoRepository, times(1)).save(any(TicketInfo.class));
        }
    }

    // ==========================================
    // READ TICKET INFO TESTS
    // ==========================================
    @Nested
    @DisplayName("getTicketInfos & getTicketInfo Tests")
    class ReadTicketInfoTests {

        @Test
        @DisplayName("Should return a list of all configured ticket infos")
        void getTicketInfos_ReturnsList() {
            TicketInfo childInfo = TicketInfo.builder().id(2L).type(TicketType.CHILD).price(BigDecimal.valueOf(50.00)).build();
            TicketInfo studentInfo = TicketInfo.builder().id(3L).type(TicketType.STUDENT).price(BigDecimal.valueOf(20.00)).build();
            when(ticketInfoRepository.findAll()).thenReturn(List.of(sampleTicketInfo, childInfo, studentInfo));

            List<TicketInfoDTO> result = ticketInfoService.getTicketInfos().getContent();

            assertThat(result).hasSize(3);
            assertThat(result.get(0).type()).isEqualTo(TicketType.ADULT);
            assertThat(result.get(1).type()).isEqualTo(TicketType.CHILD);
            assertThat(result.get(2).type()).isEqualTo(TicketType.STUDENT);
        }

        @Test
        @DisplayName("Should throw NotFoundException when fetching a non-existent ID")
        void getTicketInfo_NotFound_ThrowsNotFoundException() {
            when(ticketInfoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketInfoService.getTicketInfo(99L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Ticket info not found.");
        }

        @Test
        @DisplayName("Should return accurate DTO data when finding a valid ID")
        void getTicketInfo_ValidId_ReturnsTicketInfoDTO() {
            when(ticketInfoRepository.findById(1L)).thenReturn(Optional.of(sampleTicketInfo));

            TicketInfoDTO result = ticketInfoService.getTicketInfo(1L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.type()).isEqualTo(TicketType.ADULT);
        }
    }

    // ==========================================
    // UPDATE TICKET INFO TESTS
    // ==========================================
    @Nested
    @DisplayName("updateTicketInfo Tests")
    class UpdateTicketInfoTests {

        @Test
        @DisplayName("Should throw NotFoundException when target update ID does not exist")
        void updateTicketInfo_IdNotFound_ThrowsNotFoundException() {
            when(ticketInfoRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketInfoService.updateTicketInfo(1L, saveDto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Ticket info not found.");
        }

        @Test
        @DisplayName("Should throw AlreadyExistsException when changing type to a type that already exists elsewhere")
        void updateTicketInfo_NewTypeConflict_ThrowsAlreadyExistsException() {
            SaveTicketInfoDTO updateDto = new SaveTicketInfoDTO(TicketType.CHILD, BigDecimal.valueOf(50.00));
            TicketInfo existingVip = TicketInfo.builder().id(2L).type(TicketType.CHILD).price(BigDecimal.valueOf(50.00)).build();

            when(ticketInfoRepository.findById(1L)).thenReturn(Optional.of(sampleTicketInfo));
            when(ticketInfoRepository.findByType(TicketType.CHILD)).thenReturn(Optional.of(existingVip));

            assertThatThrownBy(() -> ticketInfoService.updateTicketInfo(1L, updateDto))
                    .isInstanceOf(AlreadyExistsException.class)
                    .hasMessageContaining("Ticket info for type 'CHILD' already exists.");
        }

        @Test
        @DisplayName("Should successfully update price and type configuration properties")
        void updateTicketInfo_ValidPayload_Success() {
            SaveTicketInfoDTO updateDto = new SaveTicketInfoDTO(TicketType.CHILD, BigDecimal.valueOf(60.00));

            when(ticketInfoRepository.findById(1L)).thenReturn(Optional.of(sampleTicketInfo));
            when(ticketInfoRepository.findByType(TicketType.CHILD)).thenReturn(Optional.empty());
            when(ticketInfoRepository.save(any(TicketInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TicketInfoDTO result = ticketInfoService.updateTicketInfo(1L, updateDto);

            assertThat(result.type()).isEqualTo(TicketType.CHILD);
            assertThat(result.price()).isEqualByComparingTo("60.00");
        }

        @Test
        @DisplayName("Should update price seamlessly if ticket type remains unchanged")
        void updateTicketInfo_SameTypePriceChange_Success() {
            SaveTicketInfoDTO updateDto = new SaveTicketInfoDTO(TicketType.ADULT, BigDecimal.valueOf(40.00));

            when(ticketInfoRepository.findById(1L)).thenReturn(Optional.of(sampleTicketInfo));
            when(ticketInfoRepository.save(any(TicketInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TicketInfoDTO result = ticketInfoService.updateTicketInfo(1L, updateDto);

            assertThat(result.type()).isEqualTo(TicketType.ADULT);
            assertThat(result.price()).isEqualByComparingTo("40.00");
            verify(ticketInfoRepository, never()).findByType(any());
        }
    }

    // ==========================================
    // DELETE TICKET INFO TESTS
    // ==========================================
    @Nested
    @DisplayName("deleteTicketInfo Tests")
    class DeleteTicketInfoTests {

        @Test
        @DisplayName("Should throw NotFoundException when deleting a record that does not exist")
        void deleteTicketInfo_NotFound_ThrowsNotFoundException() {
            when(ticketInfoRepository.existsById(1L)).thenReturn(false);

            assertThatThrownBy(() -> ticketInfoService.deleteTicketInfo(1L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Ticket info not found.");

            verify(ticketInfoRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("Should successfully trigger database deletion when ID exists")
        void deleteTicketInfo_ValidId_Success() {
            when(ticketInfoRepository.existsById(1L)).thenReturn(true);

            ticketInfoService.deleteTicketInfo(1L);

            verify(ticketInfoRepository, times(1)).deleteById(1L);
        }
    }
}
