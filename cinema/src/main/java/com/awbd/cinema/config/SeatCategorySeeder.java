package com.awbd.cinema.config;

import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.enums.SeatCategoryType;
import com.awbd.cinema.repositories.SeatCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SeatCategorySeeder implements CommandLineRunner {

    private final SeatCategoryRepository seatCategoryRepository;

    @Override
    public void run(String... args) {
        seed(SeatCategoryType.STANDARD, BigDecimal.ZERO, 0);
        seed(SeatCategoryType.VIP, new BigDecimal("5.00"), 10);
    }

    private void seed(SeatCategoryType type, BigDecimal extraFee, int extraPoints) {
        if (seatCategoryRepository.findByType(type).isEmpty()) {
            seatCategoryRepository.save(SeatCategory.builder()
                    .type(type)
                    .extraFee(extraFee)
                    .extraPoints(extraPoints)
                    .build());
        }
    }
}
