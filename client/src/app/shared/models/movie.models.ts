export interface AdminMovieResponse {
    id: number;
    title: string;
    imageUrl: string;
    releaseDate: string;
    genreIds: number[];
    rating: number;
    description: string;
}

export interface MovieResponse {
    id: number;
    title: string;
    imageUrl: string;
    releaseDate: string;
    description: string;
    rating: number;
    duration: number;
    ageRating: string;
}

export interface SaveMovieResponse {
    title: string;
    releaseDate: string;
    description: string;
    rating: number;
    duration: number;
    ageRating: string;
}