package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.SessionInfoDTOs.SaveSessionInfoDTO;
import com.awbd.cinema.DTOs.SessionInfoDTOs.SessionInfoDTO;
import com.awbd.cinema.services.SessionInfoService.SessionInfoService;
import com.awbd.cinema.utils.RestPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/session-infos")
@RequiredArgsConstructor
public class SessionInfoController {

    private final SessionInfoService sessionInfoService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<SessionInfoDTO> createSessionInfo(@Valid @RequestBody SaveSessionInfoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionInfoService.createSessionInfo(dto));
    }

    @GetMapping
    public ResponseEntity<RestPage<SessionInfoDTO>> getSessionInfos(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(sessionInfoService.getSessionInfos(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionInfoDTO> getSessionInfo(@PathVariable Long id) {
        return ResponseEntity.ok(sessionInfoService.getSessionInfo(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<SessionInfoDTO> updateSessionInfo(@PathVariable Long id, @Valid @RequestBody SaveSessionInfoDTO dto) {
        return ResponseEntity.ok(sessionInfoService.updateSessionInfo(id, dto));
    }
}
