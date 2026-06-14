package com.awbd.cinema.security;

import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationProviderTest {

    @Mock private UserDetailsService userDetailsService;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private LoginAttemptService loginAttemptService;

    private CustomAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        authenticationProvider = new CustomAuthenticationProvider(
                userDetailsService,
                passwordEncoder,
                loginAttemptService
        );
    }

    @Test
    void authenticate_ShouldThrowLockedException_WhenUserIsBlocked() {
        // Branch 1: User is flagged as blocked by the login attempt tracker
        String username = "blocked_user";
        Authentication authRequest = new UsernamePasswordAuthenticationToken(username, "password123");
        when(loginAttemptService.isBlocked(username)).thenReturn(true);

        assertThatThrownBy(() -> authenticationProvider.authenticate(authRequest))
                .isInstanceOf(LockedException.class)
                .hasMessageContaining("Too many attempts for this account.");
    }

    @Test
    void authenticate_ShouldSucceed_WhenPrincipalIsNotCustomUserDetails() {
        // Branch 2 & 3: User is not blocked, and principal is standard UserDetails (not CustomUserDetails)
        String username = "standard_user";
        Authentication authRequest = new UsernamePasswordAuthenticationToken(username, "password123");
        UserDetails standardUserDetails = mock(UserDetails.class);

        when(standardUserDetails.isAccountNonLocked()).thenReturn(true);
        when(standardUserDetails.isEnabled()).thenReturn(true);
        when(standardUserDetails.isAccountNonExpired()).thenReturn(true);
        when(standardUserDetails.isCredentialsNonExpired()).thenReturn(true);

        when(loginAttemptService.isBlocked(username)).thenReturn(false);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(standardUserDetails);
        when(standardUserDetails.getPassword()).thenReturn("encoded_password");
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(authRequest);

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void authenticate_ShouldThrowDisabledException_WhenCustomUserDetailsHasNullEmailVerifiedAt() {
        // Branch 2, 4 & 5: User not blocked, principal is CustomUserDetails, but email is unverified (null)
        String username = "unverified_user";
        Authentication authRequest = new UsernamePasswordAuthenticationToken(username, "password123");

        User rawUser = User.builder()
                .id(1L)
                .username(username)
                .password("encoded_password")
                .role(Role.USER)
                .emailVerifiedAt(null) // Triggers the missing validation condition
                .build();
        CustomUserDetails customUserDetails = new CustomUserDetails(
                rawUser.getId(), rawUser.getUsername(), rawUser.getPassword(),
                rawUser.getRole(), rawUser.getEmailVerifiedAt());

        when(loginAttemptService.isBlocked(username)).thenReturn(false);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(customUserDetails);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authenticationProvider.authenticate(authRequest))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("Email not verified.");
    }

    @Test
    void authenticate_ShouldFullySucceed_WhenCustomUserDetailsHasVerifiedEmail() {
        // Branch 2, 4 & 6: User not blocked, principal is CustomUserDetails, and email verification exists
        String username = "verified_user";
        Authentication authRequest = new UsernamePasswordAuthenticationToken(username, "password123");

        User rawUser = User.builder()
                .id(2L)
                .username(username)
                .password("encoded_password")
                .role(Role.USER)
                .emailVerifiedAt(LocalDateTime.now())
                .build();
        CustomUserDetails customUserDetails = new CustomUserDetails(
                rawUser.getId(), rawUser.getUsername(), rawUser.getPassword(),
                rawUser.getRole(), rawUser.getEmailVerifiedAt());

        when(loginAttemptService.isBlocked(username)).thenReturn(false);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(customUserDetails);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(authRequest);

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isInstanceOf(CustomUserDetails.class);

        CustomUserDetails principal = (CustomUserDetails) result.getPrincipal();
        assertThat(principal.getEmailVerifiedAt()).isNotNull();
    }
}