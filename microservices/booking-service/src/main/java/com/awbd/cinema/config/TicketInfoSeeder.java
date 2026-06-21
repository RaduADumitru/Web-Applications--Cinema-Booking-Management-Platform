package com.awbd.cinema.config;

import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.repositories.TicketInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class TicketInfoSeeder implements CommandLineRunner {

    private final TicketInfoRepository ticketInfoRepository;

    @Override
    public void run(String... args) {
        seed(TicketType.ADULT, new BigDecimal("25.00"));
        seed(TicketType.STUDENT, new BigDecimal("18.00"));
        seed(TicketType.CHILD, new BigDecimal("12.00"));
    }

    private void seed(TicketType type, BigDecimal price) {
        if (ticketInfoRepository.findByType(type).isEmpty()) {
            ticketInfoRepository.save(TicketInfo.builder()
                    .type(type)
                    .price(price)
                    .build());
        }
    }
}
