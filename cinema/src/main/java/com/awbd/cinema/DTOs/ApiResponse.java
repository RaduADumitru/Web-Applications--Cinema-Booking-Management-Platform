package com.awbd.cinema.DTOs;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Object details
) {
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static ApiResponse<Void> error(String message, Object details) {
        return new ApiResponse<>(false, null, message, details);
    }
}