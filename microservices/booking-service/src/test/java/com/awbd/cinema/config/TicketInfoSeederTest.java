package com.awbd.cinema.config;

import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.repositories.TicketInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketInfoSeederTest {

    @Mock
    private TicketInfoRepository ticketInfoRepository;

    @InjectMocks
    private TicketInfoSeeder seeder;

    @Test
    void run_seedsAllTypes_whenNonePresent() {
        when(ticketInfoRepository.findByType(any(TicketType.class))).thenReturn(Optional.empty());

        seeder.run();

        // ADULT + STUDENT + CHILD
        verify(ticketInfoRepository, times(3)).save(any(TicketInfo.class));
    }

    @Test
    void run_skipsSave_whenTypeAlreadyPresent() {
        when(ticketInfoRepository.findByType(any(TicketType.class)))
                .thenReturn(Optional.of(mock(TicketInfo.class)));

        seeder.run();

        verify(ticketInfoRepository, never()).save(any(TicketInfo.class));
    }

    @Test
    void run_doesNotFail_whenAnotherInstanceSeedsConcurrently() {
        // Both instances start together, both see "empty", both try to insert -> the loser hits the
        // unique constraint on `type`. The seeder must tolerate this so its instance still starts.
        lenient().when(ticketInfoRepository.findByType(any(TicketType.class))).thenReturn(Optional.empty());
        when(ticketInfoRepository.save(any(TicketInfo.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatCode(() -> seeder.run()).doesNotThrowAnyException();
    }
}
