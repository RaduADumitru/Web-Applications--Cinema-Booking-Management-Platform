package com.awbd.cinema.security;

import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;


@Component
public class CustomAuthenticationProvider extends DaoAuthenticationProvider {

    private final LoginAttemptService loginAttemptService;

    public CustomAuthenticationProvider(UserDetailsService userDetailsService,
                                        BCryptPasswordEncoder passwordEncoder,
                                        LoginAttemptService loginAttemptService) {
        super(userDetailsService);
        this.setPasswordEncoder(passwordEncoder);
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public Authentication authenticate(Authentication authentication){
        String username = authentication.getName();

        if (loginAttemptService.isBlocked(username)) {
            throw new LockedException("Too many attempts for this account. Try again later.");
        }

        Authentication auth = super.authenticate(authentication);

        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails user) {
            if (user.getEmailVerifiedAt() == null) {
                throw new DisabledException("Email not verified.");
            }
        }

        return auth;
    }
}