package com.awbd.cinema.services.MovieService;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;

import java.util.List;

public interface MovieService {
    List<AdminMovieDTO> getAdminMovieList(Integer page);
}
