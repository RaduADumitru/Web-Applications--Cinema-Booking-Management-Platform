package com.awbd.cinema.services.AuthService;

import com.awbd.cinema.DTOs.AuthDTOs.*;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.InvalidFieldException;
import com.awbd.cinema.exceptions.TooManyRequestsException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import com.awbd.cinema.utils.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${auth.cookie.secure}")
    private boolean cookieSecure;

    @Value("${auth.cookie.same-site}")
    private String cookieSameSite;

    @Override
    @Transactional
    public RegisterResponseDTO register(RegisterDTO register){
        validateUserUniqueness(register);

        User u = maptoEntity(register);

        userRepository.save(u);
        return new RegisterResponseDTO("Account created successfully.",u.getUsername());
    }

    @Override
    public LoginActionDTO login(LoginDTO login){
        if (loginAttemptService.isBlocked(login.username())) {
            throw new TooManyRequestsException("Too many attempts. Try again later.");
        }
        Authentication auth;
        try {
            auth =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(login.username(), login.password()));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (BadCredentialsException e) {
            loginAttemptService.loginFailed(login.username());
            throw new InvalidFieldException("Invalid account details.");
        } catch (LockedException e) {
            throw new TooManyRequestsException("Too many attempts. Try again later.");
        } catch (DisabledException e) {
            throw new InvalidFieldException("Email is not verified.");
        }

        User u =
                userRepository
                        .findByUsernameIgnoreCase(auth.getName())
                        .orElseThrow(() -> new InvalidFieldException("Invalid account details."));

        if (u.getDeletedAt() != null) {
            loginAttemptService.loginFailed(login.username());
            throw new InvalidFieldException("Invalid account details.");
        }

        loginAttemptService.loginSucceeded(login.username());

        ResponseCookie jwtCookie = createJwtCookie(u.getUsername());
        ResponseCookie refreshCookie = createRefreshCookie(u.getUsername());

        return new LoginActionDTO(LoginResponseDTO.from(u), new LoginCookiesDTO(jwtCookie,refreshCookie));
    }

    @Override
    public void createOwner(RegisterDTO owner){
        userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(owner.username(),owner.email())
                .orElseGet(()->{
                    User u = maptoEntity(owner);
                    u.setRole(Role.OWNER);

                    log.info("Created owner: {}", u.getUsername());
                    return userRepository.save(u);
                });
    }

    private void validateUserUniqueness(RegisterDTO register){
        if (userRepository.existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(register.username(), register.email())) {
            throw new AlreadyExistsException("Username or email address is already in use.");
        }
    }

    private User maptoEntity(RegisterDTO dto){
        return User.builder()
                .username(dto.username())
                .email(dto.email())
                .password(passwordEncoder.encode(dto.password()))
                .firstName(HtmlUtils.htmlEscape(dto.firstName()))
                .lastName(HtmlUtils.htmlEscape(dto.lastName()))
                .phoneNumber(dto.phoneNumber())
                .build();
    }

    private ResponseCookie createJwtCookie(String username) {
        String token = jwtUtil.generateToken(username);
        return ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(3 * 60 * 60) // 3 hours
                .sameSite(cookieSameSite)
                .build();
    }

    private ResponseCookie createRefreshCookie(String username) {
        String token = jwtUtil.generateRefreshToken(username);
        return ResponseCookie.from("refresh", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(60L * 60L * 24L * 30L) // 30 days
                .sameSite(cookieSameSite)
                .build();
    }
}
