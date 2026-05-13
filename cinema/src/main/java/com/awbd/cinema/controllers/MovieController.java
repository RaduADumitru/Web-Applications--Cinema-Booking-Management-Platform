package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.services.MovieService.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @RequestMapping("/admin/list")
    @PreAuthorize("hasRole('STAFF')")
    public List<AdminMovieDTO> getAdminMovieList(@RequestParam(defaultValue = "1") Integer page){
        return movieService.getAdminMovieList(page);
    }
}
