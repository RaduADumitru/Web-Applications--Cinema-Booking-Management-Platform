import { Component, OnInit, signal } from '@angular/core';
import { MovieResponse } from '@app/shared/models/movie.models';
import { MovieService } from '@app/core/services/movie.service';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

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

  constructor(private movieService: MovieService, private router: Router) {}

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
}