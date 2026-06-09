package com.awbd.cinema.security;

import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("cinema_goer")
                .password("secure_password")
                .role(Role.USER)
                .emailVerifiedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void loadUserByUsername_ShouldReturnCustomUserDetails_WhenUserExists() {
        String username = "cinema_goer";
        when(userRepository.findByUsernameIgnoreCase(username)).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(username);
        assertThat(userDetails.getPassword()).isEqualTo("secure_password");
        assertThat(userDetails.getAuthorities()).hasSize(1);
        assertThat(userDetails.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void loadUserByUsername_ShouldThrowUsernameNotFoundException_WhenUserDoesNotExist() {
        String username = "ghost_user";
        when(userRepository.findByUsernameIgnoreCase(username)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername(username))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage(username);
    }
}