import { Component, OnInit, signal } from '@angular/core';
import { MovieResponse } from '@app/shared/models/movie.models';
import { MovieService } from '@app/core/services/movie.service';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { UserService } from '@services/user.service';
import { FormsModule } from '@angular/forms'; // Imported for form data binding
import Swal from 'sweetalert2';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule], // Added FormsModule here
  templateUrl: './home.html',
  styleUrls: ['./home.css'],
})
export class HomeComponent implements OnInit {
  movies = signal<MovieResponse[]>([]);
  isLoading = signal<boolean>(true);

  // Filter Properties bound to template
  searchTitle: string = '';
  minRating: number | null = null;
  maxRating: number | null = null;
  ageRating: string = '';
  releaseFrom: string = '';
  releaseTo: string = '';
  genre: string = '';

  constructor(private movieService: MovieService, private router: Router, private userService: UserService) {}

  ngOnInit(): void {
    this.loadMovies();
  }

  // Generalized data loader that applies current filters
  loadMovies(): void {
    this.isLoading.set(true);
    this.movieService.getMovies(
      1,
      10,
      this.searchTitle || undefined,
      this.minRating !== null ? this.minRating : undefined,
      this.maxRating !== null ? this.maxRating : undefined,
      this.ageRating || undefined,
      this.releaseFrom || undefined,
      this.releaseTo || undefined,
      this.genre || undefined
    ).subscribe({
      next: (response) => {
        this.movies.set(response.content);
        this.isLoading.set(false);
      },
      error: (error) => {
        console.error(error);
        this.isLoading.set(false);
      }
    });
  }

  // Clear all filters back to default empty states
  resetFilters(): void {
    this.searchTitle = '';
    this.minRating = null;
    this.maxRating = null;
    this.ageRating = '';
    this.releaseFrom = '';
    this.releaseTo = '';
    this.genre = '';
    this.loadMovies();
  }

  goToAdminDashboard(): void {
    this.router.navigate(['/admin']);
  }

  isAdmin(): boolean {
    return this.userService.hasRole('OWNER'); 
  }

  formatGenres(genres: string[] | undefined): string {
    if (!genres || genres.length === 0) return '';
    
    return genres
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(', ');
  }

  deleteMovie(id: number): void {
    Swal.fire({
      title: 'Are you sure?',
      text: "You won't be able to revert this!",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Yes, delete it!',
      customClass: {
        popup: 'swal-bg' 
      }
    }).then((result) => {
      if (result.isConfirmed) {
        this.movieService.deleteMovie(id).subscribe({
          next: () => {
            this.movies.set(this.movies().filter(movie => movie.id !== id));
            
            Swal.fire({
              title: 'Deleted!',
              text: 'The movie has been deleted.',
              icon: 'success',
              customClass: {
                popup: 'swal-bg' 
              }
            });
          },
          error: (error) => {
            console.error('Error deleting movie:', error);
            
            Swal.fire({
              title: 'Error!',
              text: 'An error occurred while deleting the movie. Please try again. ' + 
                    (error.error?.message ? `Details: ${error.error.message}` : ''),
              icon: 'error',
              customClass: {
                popup: 'swal-bg' 
              }
            });
          }
        });
      }
    });
  } 
}