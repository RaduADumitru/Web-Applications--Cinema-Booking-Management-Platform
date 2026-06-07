import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse, PagedResponse } from '@app/shared/models/api.models';
import { ApiService } from './api.service';
import { MovieResponse, SaveMovieResponse, AdminMovieResponse } from '@app/shared/models/movie.models';

@Injectable({
    providedIn: 'root'
})
export class MovieService {

    constructor(private api: ApiService) {}

    getMovies(
        page: number = 1,
        size: number = 10,
        title?: string,
        minRating?: number,
        maxRating?: number,
        ageRating?: string,
        releaseFrom?: string,
        releaseTo?: string
    ): Observable<PagedResponse<MovieResponse>> {
        const params: any = {};
        if (title != null) params.title = title;
        if (minRating != null) params.minRating = minRating;
        if (maxRating != null) params.maxRating = maxRating;
        if (ageRating != null) params.ageRating = ageRating;
        if (releaseFrom != null) params.releaseFrom = releaseFrom;
        if (releaseTo != null) params.releaseTo = releaseTo;

        return this.api.getPaged<MovieResponse>('/movies', page, size, params);
    }

    getMovieById(id: number): Observable<MovieResponse> {
        return this.api.get<MovieResponse>(`/movies/${id}`);
    }

    createMovie(movie: SaveMovieResponse): Observable<MovieResponse>{
        return this.api.post<MovieResponse>('/movies', movie);
    }

    updateMovie(id: number, movie: SaveMovieResponse): Observable<MovieResponse> {
        return this.api.put<MovieResponse>(`/movies/${id}`, movie);
    }

    deleteMovie(id: number): Observable<void> {
        return this.api.delete<void>(`/movies/${id}`);
    }

    getAdminMovies(page: number = 0, size: number = 8): Observable<PagedResponse<AdminMovieResponse>> {
        return this.api.getPaged<AdminMovieResponse>('/movies/admin/list', page, size).pipe(
            map((response: any) => ({
                content: response.content,
                page: {
                    number: response.page.number,
                    size: response.page.size,
                    totalElements: response.page.totalElements,
                    totalPages: response.page.totalPages
                }
            }))
        );
    }

    saveAdminMovie(tmdbId: number): Observable<SaveMovieResponse> {
        return this.api.post<SaveMovieResponse>(`/movies/admin/save/${tmdbId}`, {});
    }
}