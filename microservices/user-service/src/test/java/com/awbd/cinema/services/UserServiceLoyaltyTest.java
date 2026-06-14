package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.services.UserService.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceLoyaltyTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserServiceImpl userService;

    @Test
    void getLoyaltyPoints_ReturnsCurrentBalance() {
        User user = User.builder().id(5L).username("bob").loyaltyPoints(120).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        LoyaltyPointsDTO result = userService.getLoyaltyPoints(5L);

        assertEquals(5L, result.userId());
        assertEquals(120, result.loyaltyPoints());
    }

    @Test
    void getLoyaltyPoints_ThrowsNotFound_WhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getLoyaltyPoints(99L));
    }

    @Test
    void updateLoyaltyPoints_SetsAbsoluteValueAndPersists() {
        User user = User.builder().id(5L).username("bob").loyaltyPoints(120).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        LoyaltyPointsDTO result = userService.updateLoyaltyPoints(5L, new AdjustLoyaltyPointsDTO(45));

        assertEquals(5L, result.userId());
        assertEquals(45, result.loyaltyPoints());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(45, captor.getValue().getLoyaltyPoints());
    }

    @Test
    void updateLoyaltyPoints_ThrowsNotFound_WhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userService.updateLoyaltyPoints(99L, new AdjustLoyaltyPointsDTO(10)));
        verify(userRepository, never()).save(any(User.class));
    }
}
