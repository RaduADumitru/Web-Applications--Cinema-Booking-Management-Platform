package com.awbd.cinema.config;

import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.repositories.TicketInfoRepository;
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
public class TicketInfoSeeder implements CommandLineRunner {

    private final TicketInfoRepository ticketInfoRepository;

    @Override
    public void run(String... args) {
        seed(TicketType.ADULT, new BigDecimal("25.00"));
        seed(TicketType.STUDENT, new BigDecimal("18.00"));
        seed(TicketType.CHILD, new BigDecimal("12.00"));
    }

    private void seed(TicketType type, BigDecimal price) {
        if (ticketInfoRepository.findByType(type).isPresent()) {
            return;
        }
        try {
            ticketInfoRepository.save(TicketInfo.builder()
                    .type(type)
                    .price(price)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // The existence check above is racy: when several instances start together they can all
            // see "absent" and insert, so the losers hit the unique constraint on `type`. Treat that
            // as benign (another instance already seeded it) instead of failing this instance's start.
            log.info("Ticket info {} already created by another instance, skipping.", type);
        }
    }
}
