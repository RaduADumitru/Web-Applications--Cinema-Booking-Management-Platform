package com.awbd.cinema.services.UserService;

import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.PromoteDTO;
import com.awbd.cinema.DTOs.UserDTOs.UpdateProfileDTO;
import com.awbd.cinema.security.CustomUserDetails;

import com.awbd.cinema.utils.RestPage;

import java.util.Map;

public interface UserService {
    ProfileDTO getProfile(Long id);
    Map<String,String> deleteAccount(CustomUserDetails userDetails);
    Map<String,String> deleteAccount(Long id);
    ProfileDTO updateProfile(Long id, UpdateProfileDTO dto);
    ProfileDTO promoteUser(PromoteDTO dto);
    RestPage<ProfileDTO> getAllUsers(Integer page, Integer size);
}
