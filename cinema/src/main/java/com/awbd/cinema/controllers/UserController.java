package com.awbd.cinema.controllers;


import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.UpdateProfileDTO;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.services.UserService.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ProfileDTO> getUser(@AuthenticationPrincipal CustomUserDetails userDetails){
        return ResponseEntity.ok(userService.getProfile(userDetails));
    }

    @DeleteMapping
    public ResponseEntity<Map<String,String>> deleteUser(@AuthenticationPrincipal CustomUserDetails userDetails){
        return ResponseEntity.ok(userService.deleteAccount(userDetails));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String,String>> deleteUser(@PathVariable Long id){
        return ResponseEntity.ok(userService.deleteAccount(id));
    }

    @PatchMapping
    public ResponseEntity<ProfileDTO> updateProfile(@AuthenticationPrincipal CustomUserDetails userDetails,@Valid @RequestBody UpdateProfileDTO dto) {
        return ResponseEntity.ok(userService.updateProfile(userDetails, dto));
    }
}
