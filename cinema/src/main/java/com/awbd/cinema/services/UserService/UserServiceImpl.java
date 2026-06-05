package com.awbd.cinema.services.UserService;

import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.UpdateProfileDTO;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.security.CustomUserDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;

    public ProfileDTO getProfile(CustomUserDetails userDetails){
        User u = userRepository.findById(userDetails.getId()).orElseThrow(()->new NotFoundException("User doesn't exist."));
        return ProfileDTO.from(u);
    }

    @Override
    @Transactional
    public Map<String, String> deleteAccount(CustomUserDetails userDetails) {
        deleteUser(userDetails.getId());
        return Map.of("message", "Your account has been deleted successfully.");
    }

    @Override
    @Transactional
    public Map<String, String> deleteAccount(Long id) {
        String deletedUsername = deleteUser(id);
        return Map.of("message", deletedUsername + "'s account has been deleted successfully.");
    }

    @Transactional
    public ProfileDTO updateProfile(CustomUserDetails customUserDetails, UpdateProfileDTO dto) {
        User user = userRepository.findById(customUserDetails.getId())
                .orElseThrow(() -> new RuntimeException("User doesn't exist"));

        if (dto.firstName() != null) {
            user.setFirstName(dto.firstName());
        }

        if (dto.lastName() != null) {
            user.setLastName(dto.lastName());
        }

        if (dto.phoneNumber() != null) {
            user.setPhoneNumber(dto.phoneNumber());
        }

        if (dto.email() != null && !user.getEmail().equalsIgnoreCase(dto.email())) {
            userRepository.findByEmailIgnoreCase(dto.email()).ifPresent(existingUser -> {
                throw new IllegalArgumentException("Email address is already in use.");
            });
            user.setEmail(dto.email());
        }
        userRepository.save(user);
        return ProfileDTO.from(user);
    }

    private String deleteUser(Long id){
        User u = userRepository.findById(id).orElseThrow(()->new NotFoundException("User doesn't exist."));
        UUID uuid = UUID.randomUUID();
        u.setUsername(u.getUsername() + "-deleted" + uuid);
        u.setEmail("deleted-" + uuid + "@example.com");
        u.setPhoneNumber("+0777777777");
        u.setFirstName("Deleted");
        u.setLastName("User");
        u.setDeletedAt(LocalDateTime.now());
        userRepository.save(u);
        return u.getUsername();
    }


}
