import { Component, OnInit, signal } from '@angular/core';
import { MovieResponse } from '@app/shared/models/movie.models';
import { MovieService } from '@app/core/services/movie.service';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { UserService } from '@services/user.service';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './home.html',
  styleUrls: ['./home.css'],
})

export class HomeComponent implements OnInit {
  movies = signal<MovieResponse[]>([]);
  isLoading = signal<boolean>(true);

  constructor(private movieService: MovieService, private router: Router, private userService: UserService) {}

  ngOnInit(): void {
    this.movieService.getMovies().subscribe({
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

  goToAdminDashboard(): void {
    this.router.navigate(['/admin']);
  }

  isAdmin(): boolean {
    return this.userService.hasRole('OWNER'); 
  }

  deleteMovie(id: number): void {
    Swal.fire({
      title: 'Are you sure?',
      text: "You won't be able to revert this!",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Yes, delete it!'
    }).then((result) => {
      if (result.isConfirmed) {
        this.movieService.deleteMovie(id).subscribe({
          next: () => {
            this.movies.set(this.movies().filter(movie => movie.id !== id));
            Swal.fire(
              'Deleted!',
              'The movie has been deleted.',
              'success'
            );
          },
          error: (error) => {
          console.error('Error deleting movie:', error);
          Swal.fire(
            'Error!',
            'An error occurred while deleting the movie. Please try again.' + (error.error?.message ? `Details: ${error.error.message}` : ''),
            'error'
          );
        }
        });
      };  
    });
  } 
}