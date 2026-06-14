package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.MovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.SaveMovieDTO;
import com.awbd.cinema.services.MovieService.MovieService;
import com.awbd.cinema.utils.RestPage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @GetMapping("/admin/list")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<RestPage<AdminMovieDTO>> getAdminMovieList(@RequestParam(defaultValue = "1") Integer page){
        return ResponseEntity.ok(movieService.getAdminMovieList(page));
    }

    @PostMapping("/admin/save/{tmdbId}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<SaveMovieDTO> saveMovie(@PathVariable int tmdbId){
        return ResponseEntity.ok(movieService.saveMovie(tmdbId));
    }

    @GetMapping
    public ResponseEntity<RestPage<MovieDTO>> getMovies(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) String ageRating,
            @RequestParam(required = false) String releaseFrom,
            @RequestParam(required = false) String releaseTo,
            @RequestParam(required = false) String genre
    ){
        return ResponseEntity.ok(movieService.getUserMovieList(page, size, title, minRating, maxRating, ageRating, releaseFrom, releaseTo, genre));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieDTO> getMovie(@PathVariable Long id){
        return ResponseEntity.ok(movieService.getMovie(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<MovieDTO> updateMovie(@PathVariable Long id, @RequestBody SaveMovieDTO dto){
        return ResponseEntity.ok(movieService.updateMovie(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> deleteMovie(@PathVariable Long id){
        movieService.deleteMovie(id);
        return ResponseEntity.noContent().build();
    }
}
