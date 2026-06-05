package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.services.NotificationService.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<NotificationDTO> createNotification(
            @Valid @RequestBody CreateNotificationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createNotification(dto));
    }

    @GetMapping("/my")
    public ResponseEntity<List<NotificationDTO>> getMyNotifications(Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(notificationService.getMyNotifications(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> getNotification(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getNotification(id));
    }

    @PatchMapping("/{id}/send")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<NotificationDTO> markAsSent(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsSent(id));
    }

}
