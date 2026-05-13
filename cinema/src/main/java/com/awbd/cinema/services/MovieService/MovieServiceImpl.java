package com.awbd.cinema.services.MovieService;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.exceptions.BadRequestException;
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.tools.TmdbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieServiceImpl implements MovieService {

    private final TmdbApi tmdbApi;

    public List<AdminMovieDTO> getAdminMovieList(Integer page){
        try {
            return tmdbApi.getMovieLists().getPopular(
                    "en-US", page, "GBR").getResults().stream()
                    .map((AdminMovieDTO::from)).toList();
        }
        catch (TmdbException e){
            log.error("Error while fetching movies: {}", e.getMessage());
            throw new BadRequestException("Error while fetching movies.");
        }
    }
}
