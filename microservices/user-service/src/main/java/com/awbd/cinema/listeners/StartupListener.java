package com.awbd.cinema.listeners;

import com.awbd.cinema.DTOs.AuthDTOs.RegisterDTO;
import com.awbd.cinema.services.AuthService.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupListener {
    private final AuthService authService;

    @Value("${bootstrap.owner-password}")
    private String adminPassword;

    @Value("${bootstrap.owner-username}")
    private String adminUsername;

    @Value("${bootstrap.owner-email}")
    private String adminEmail;

    @Value("${bootstrap.owner-first-name}")
    private String adminFirstName;

    @Value("${bootstrap.owner-last-name}")
    private String adminLastName;

    @Value("${bootstrap.owner-phone-number}")
    private String adminPhoneNumber;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        authService.createOwner(new RegisterDTO(
                adminUsername,
                adminPassword,
                adminPassword,
                adminEmail,
                adminFirstName,
                adminLastName,
                adminPhoneNumber
        ));
    }
}