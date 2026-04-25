package com.awbd.cinema.DTOs.AuthDTOs;

import com.awbd.cinema.validators.PasswordMatch;
import jakarta.validation.constraints.*;

@PasswordMatch
public record RegisterDTO(
        @NotEmpty(message = "Numele de utilizator nu poate fi gol.")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{3,20}$",
                message =
                        "Numele de utilizator conține caractere invalide sau nu respectă dimensiunile (3-20 de caractere).")
        String username,
        @NotBlank(message = "Parola nu poate fi goală.")
        @Size(min = 8)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message =
                        "Parola trebuie sa includă cel puțin o litera mare, una mica, un caracter special si cel putin 8 caractere")
        String password,
        @NotBlank(message = "Parola nu poate fi goală.")
        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message =
                        "Parola trebuie sa includă cel puțin o litera mare, una mica, un caracter special si cel putin 8 caractere")
        String confirmPassword,
        @NotEmpty(message = "Adresa de email nu poate fi goala.")
        @Email(message = "Adresa de email este invalida.")
        String email,
        @NotEmpty(message = "Prenumele nu poate fi gol.")
        @Pattern(
                regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$",
                message = "Prenumele este invalid")
        String firstName,
        @NotEmpty(message = "Numele de familie nu poate fi gol.")
        @Pattern(
                regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$",
                message = "Numele de familie este invalid")
        String lastName,
        @NotBlank(message = "Introdu numărul tau de telefon.")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Numărul de telefon este invalid.")
        String phoneNumber
        ){
}
