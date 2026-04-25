package com.awbd.cinema.entities;

import com.awbd.cinema.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Username-ul este obligatoriu.")
    private String username;

    @Column(name="first_name", nullable = false)
    @NotBlank(message = "Prenumele nu poate fi gol.")
    @Pattern(regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$", message = "Prenumele este invalid")
    private String firstName;

    @Column(name="last_name", nullable = false)
    @NotBlank(message = "Numele de familie nu poate fi gol.")
    @Pattern(regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$", message = "Numele de familie este invalid")
    private String lastName;

    @Column(unique = true, nullable = false)
    @Email(message = "Adresa de email este invalida.")
    @NotBlank(message = "Adresa de email nu poate fi goala.")
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name="phone_number", nullable = false)
    @NotBlank(message = "Introdu numarul de telefon")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Numărul de telefon este invalid.")
    private String phoneNumber;

    @Column(name = "loyalty_points")
    @Builder.Default
    private Integer loyaltyPoints = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime deletedAt;


    @Column(name = "email_verified_at")
    @Builder.Default
    private LocalDateTime emailVerifiedAt = LocalDateTime.now(); //placeholder, it will actually be set via email verification in a future update
}