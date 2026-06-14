package com.awbd.cinema.services.MovieService;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.MovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.SaveMovieDTO;
import com.awbd.cinema.utils.RestPage;

public interface MovieService {
    RestPage<AdminMovieDTO> getAdminMovieList(Integer page);
    SaveMovieDTO saveMovie(int adminMovieId);

    RestPage<MovieDTO> getUserMovieList(Integer page, Integer size, String title, Double minRating, Double maxRating, String ageRating, String releaseFrom, String releaseTo, String genre);

    MovieDTO getMovie(Long id);

    MovieDTO updateMovie(Long id, SaveMovieDTO dto);

    void deleteMovie(Long id);
}
