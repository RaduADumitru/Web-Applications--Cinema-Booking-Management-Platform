package com.awbd.cinema.DTOs.UserDTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileDTO(
        @Pattern(regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$", message = "Invalid first name.")
        String firstName,
        @Pattern(regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$", message = "Invalid last name.")
        String lastName,
        @Email(message = "Email address is not correct.")
        String email,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone number is invalid.")
        String phoneNumber
){}
