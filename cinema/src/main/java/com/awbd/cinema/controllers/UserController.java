package com.awbd.cinema.controllers;


import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.PromoteDTO;
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
        return ResponseEntity.ok(userService.getProfile(userDetails.getId()));
    }

    //coupling with the service for overriding w/ admin privileges
    @DeleteMapping
    public ResponseEntity<Map<String,String>> deleteUser(@AuthenticationPrincipal CustomUserDetails userDetails){
        return ResponseEntity.ok(userService.deleteAccount(userDetails));
    }

    //read above
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String,String>> deleteUser(@PathVariable Long id){
        return ResponseEntity.ok(userService.deleteAccount(id));
    }

    @PatchMapping
    public ResponseEntity<ProfileDTO> updateProfile(@AuthenticationPrincipal CustomUserDetails userDetails,@Valid @RequestBody UpdateProfileDTO dto) {
        return ResponseEntity.ok(userService.updateProfile(userDetails.getId(), dto));
    }

    @PatchMapping("/promote")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProfileDTO> promoteUser(@Valid @RequestBody PromoteDTO dto, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if(userDetails.getId().equals(dto.id()))
            throw new IllegalArgumentException("You cannot promote yourself.");
        return ResponseEntity.ok(userService.promoteUser(dto));
    }
}
