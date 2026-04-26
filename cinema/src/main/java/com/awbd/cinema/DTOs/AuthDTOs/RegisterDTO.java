package com.awbd.cinema.DTOs.AuthDTOs;

import com.awbd.cinema.validators.PasswordMatch;
import jakarta.validation.constraints.*;

@PasswordMatch
public record RegisterDTO(
        @NotEmpty(message = "Username cannot be empty.")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{3,20}$",
                message =
                        "Username contains invalid characters or does not meet the length requirements (3-20 characters).")
        String username,

        @NotBlank(message = "Password cannot be empty.")
        @Size(min = 8)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message =
                        "Password must include at least one uppercase letter, one lowercase letter, one special character, and be at least 8 characters long.")
        String password,

        @NotBlank(message = "Password cannot be empty.")
        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message =
                        "Password must include at least one uppercase letter, one lowercase letter, one special character, and be at least 8 characters long.")
        String confirmPassword,

        @NotEmpty(message = "Email address cannot be empty.")
        @Email(message = "Email address is invalid.")
        String email,

        @NotEmpty(message = "First name cannot be empty.")
        @Pattern(
                regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$",
                message = "First name is invalid.")
        String firstName,

        @NotEmpty(message = "Last name cannot be empty.")
        @Pattern(
                regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$",
                message = "Last name is invalid.")
        String lastName,

        @NotBlank(message = "Please enter your phone number.")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone number is invalid.")
        String phoneNumber
){
}