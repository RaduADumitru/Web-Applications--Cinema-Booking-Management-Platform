import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MovieService } from '@app/core/services/movie.service';
import { AdminMovieResponse } from '@app/shared/models/movie.models';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin.html',
  styleUrl: './admin.css',
})

export class AdminComponent implements OnInit {

  tmdbMovies: AdminMovieResponse[] = [];
  loading = signal(false);
  error: string | null = null;
  currentPage = 0;
  pageSize = 8;
  totalPages = 0;
  totalElements = 0;
  pageNumbers: number[] = [];

  constructor(private movieService: MovieService) {}

  ngOnInit(): void {
    this.loadMovies(0);
  }

  loadMovies(page: number): void {
    this.loading.set(true);
    this.error = null;

    this.movieService.getAdminMovies(page + 1, this.pageSize).subscribe({
      next: (response) => {
        this.tmdbMovies = response.content;
        this.currentPage = response.page.number;
        this.totalPages = response.page.totalPages;
        this.totalElements = response.page.totalElements;
        this.pageNumbers = this.getVisiblePages();
        console.log(response);
      },
      error: (error) => {
        console.error(error);
        this.error = 'Unable to load admin movies. Please try again.';
      }
    }).add(() => {
      this.loading.set(false);
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages || page === this.currentPage) {
      return;
    }

    this.loadMovies(page);
  }

  trackByMovie(_: number, movie: AdminMovieResponse): number {
    return movie.id;
  }

  trackByPage(_: number, page: number): number {
    return page;
  }

  saveMovie(tmdbId: number): void {
    this.movieService.saveAdminMovie(tmdbId).subscribe({
      next: (response) => {
        Swal.fire({
          icon: 'success',
          title: 'Movie Saved',
          text: `The movie "${response.title}" has been saved successfully.`,
          confirmButtonText: 'OK',
      customClass: {
        popup: 'swal-bg' 
      }
        });
      },
      error: (error) => {
        Swal.fire({
          icon: 'error',
          title: 'Error',
          text: (error?.error?.message ? `${error.error.message}` : 'Failed to save the movie. Please try again.'),
          confirmButtonText: 'OK',
      customClass: {
        popup: 'swal-bg' 
      }
        });
      }
    });
  }

  getVisiblePages(): number[] {
    const maxVisiblePages = 5; 
    const pages: number[] = [];

    let startPage = Math.max(0, this.currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = startPage + maxVisiblePages - 1;

    if (endPage >= this.totalPages) {
      endPage = this.totalPages - 1;
      startPage = Math.max(0, endPage - maxVisiblePages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      pages.push(i);
    }

    return pages;
  }
  
}
