package com.awbd.cinema.config;

import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.enums.SeatCategoryType;
import com.awbd.cinema.repositories.SeatCategoryRepository;
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
class SeatCategorySeederTest {

    @Mock
    private SeatCategoryRepository seatCategoryRepository;

    @InjectMocks
    private SeatCategorySeeder seeder;

    @Test
    void run_seedsBothCategories_whenNonePresent() {
        when(seatCategoryRepository.findByType(any(SeatCategoryType.class))).thenReturn(Optional.empty());

        seeder.run();

        // STANDARD + VIP
        verify(seatCategoryRepository, times(2)).save(any(SeatCategory.class));
    }

    @Test
    void run_skipsSave_whenCategoryAlreadyPresent() {
        when(seatCategoryRepository.findByType(any(SeatCategoryType.class)))
                .thenReturn(Optional.of(mock(SeatCategory.class)));

        seeder.run();

        verify(seatCategoryRepository, never()).save(any(SeatCategory.class));
    }

    @Test
    void run_doesNotFail_whenAnotherInstanceSeedsConcurrently() {
        // Both instances start together, both see "empty", both try to insert -> the loser hits the
        // unique constraint on `type`. The seeder must tolerate this so its instance still starts.
        lenient().when(seatCategoryRepository.findByType(any(SeatCategoryType.class))).thenReturn(Optional.empty());
        when(seatCategoryRepository.save(any(SeatCategory.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatCode(() -> seeder.run()).doesNotThrowAnyException();
    }
}
