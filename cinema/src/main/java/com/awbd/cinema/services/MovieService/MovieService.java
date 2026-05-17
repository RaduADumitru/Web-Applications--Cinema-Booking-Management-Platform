package com.awbd.cinema.services.MovieService;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.SaveMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.MovieDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface MovieService {
    List<AdminMovieDTO> getAdminMovieList(Integer page);
    SaveMovieDTO saveMovie(int adminMovieId);

    Page<MovieDTO> getUserMovieList(Integer page, Integer size, String title, Double minRating, Double maxRating, String ageRating, String releaseFrom, String releaseTo);

    MovieDTO getMovie(Long id);

    MovieDTO updateMovie(Long id, SaveMovieDTO dto);

    void deleteMovie(Long id);
}
