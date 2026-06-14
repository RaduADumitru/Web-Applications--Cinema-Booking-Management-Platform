package com.awbd.cinema.services.UserService;

import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.PromoteDTO;
import com.awbd.cinema.DTOs.UserDTOs.UpdateProfileDTO;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.security.CustomUserDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;

    @Override
    @Cacheable("user_profile")
    public ProfileDTO getProfile(Long id){
        User u = userRepository.findById(id).orElseThrow(()->new NotFoundException("User doesn't exist."));
        return ProfileDTO.from(u);
    }

    @Override
    @Transactional
    @CacheEvict("user_profile")
    public Map<String, String> deleteAccount(CustomUserDetails userDetails) {
        if(userDetails.getAuthorities().stream().anyMatch(r-> Objects.equals(r.getAuthority(), "ROLE_OWNER")))
            throw new BadRequestException("You cannot delete your account.");

        deleteUser(userDetails.getId());
        return Map.of("message", "Your account has been deleted successfully.");
    }

    @Override
    @Transactional
    @CacheEvict("user_profile")
    public Map<String, String> deleteAccount(Long id) {
        String deletedUsername = deleteUser(id);
        return Map.of("message", deletedUsername + "'s account has been deleted successfully.");
    }

    @Transactional
    @Override
    @CacheEvict("user_profile")
    public ProfileDTO updateProfile(Long id, UpdateProfileDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User doesn't exist"));

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
                throw new AlreadyExistsException("Email address is already in use.");
            });
            user.setEmail(dto.email());
        }
        userRepository.save(user);
        return ProfileDTO.from(user);
    }

    @Override
    @Transactional
    @CacheEvict("user_profile")
    public ProfileDTO promoteUser(PromoteDTO dto) {
        User user = userRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestException("User doesn't exist"));
        user.setRole(dto.role());
        userRepository.save(user);
        return ProfileDTO.from(user);
    }

    private String deleteUser(Long id){
        User u = userRepository.findById(id).orElseThrow(()->new NotFoundException("User doesn't exist."));
        if(u.getDeletedAt() != null)
            throw new BadRequestException("User doesn't exist.");
        if(u.getRole() == Role.OWNER)
            throw new BadRequestException("You cannot delete this account.");

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
