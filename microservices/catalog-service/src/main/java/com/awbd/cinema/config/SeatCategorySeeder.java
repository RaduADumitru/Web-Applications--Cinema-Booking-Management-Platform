package com.awbd.cinema.config;

import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.enums.SeatCategoryType;
import com.awbd.cinema.repositories.SeatCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class SeatCategorySeeder implements CommandLineRunner {

    private final SeatCategoryRepository seatCategoryRepository;

    @Override
    public void run(String... args) {
        seed(SeatCategoryType.STANDARD, BigDecimal.ZERO, 0);
        seed(SeatCategoryType.VIP, new BigDecimal("5.00"), 10);
    }

    private void seed(SeatCategoryType type, BigDecimal extraFee, int extraPoints) {
        if (seatCategoryRepository.findByType(type).isPresent()) {
            return;
        }
        try {
            seatCategoryRepository.save(SeatCategory.builder()
                    .type(type)
                    .extraFee(extraFee)
                    .extraPoints(extraPoints)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // The existence check above is racy: when several instances start together they can all
            // see "absent" and insert, so the losers hit the unique constraint on `type`. Treat that
            // as benign (another instance already seeded it) instead of failing this instance's start.
            log.info("Seat category {} already created by another instance, skipping.", type);
        }
    }
}
