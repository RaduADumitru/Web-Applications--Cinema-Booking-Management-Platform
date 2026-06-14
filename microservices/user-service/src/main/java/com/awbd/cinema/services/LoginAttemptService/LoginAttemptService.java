package com.awbd.cinema.services.LoginAttemptService;

public interface LoginAttemptService {
    void loginFailed(String username);
    void loginSucceeded(String username);
    boolean isBlocked(String username);
}