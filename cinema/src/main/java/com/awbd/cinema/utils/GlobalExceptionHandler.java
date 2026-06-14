package com.awbd.cinema.utils;

import com.awbd.cinema.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyExists(AlreadyExistsException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidField(InvalidFieldException e) {
        return buildResponse(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), null);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequests(TooManyRequestsException e) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthenticated(UnauthenticatedException e) {
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            log.warn("Unauthenticated access attempt to a protected resource.");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Unauthorized",
                            "message", "Full authentication is required to access this resource."
                    ));
        }

        log.warn("User '{}' was denied access to a protected resource.", auth.getName());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "Forbidden",
                        "message", "You do not have permission to access this resource."
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        return buildResponse(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed.", errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("Unhandled exception while processing request.", e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", null);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);

        if (details != null) {
            body.put("details", details);
        }

        return new ResponseEntity<>(body, status);
    }
}