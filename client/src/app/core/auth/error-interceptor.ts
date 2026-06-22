import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '@services/auth.service';
import { UserService } from '@services/user.service';
import { Router } from '@angular/router';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  const authService = inject(AuthService);
  const userService = inject(UserService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage = 'An error occurred';

      if (error.error instanceof ErrorEvent) {
        // Client-side error
        errorMessage = error.error.message;
      } else {
        // Server-side error
        if (error.status === 0) {
          errorMessage = 'Unable to connect to the server. Please check your connection.';
        } else if (error.status === 400) {
          errorMessage = error.error?.message || 'Bad request';
        } else if (error.status === 401) {
          const rememberMe = localStorage.getItem('rememberMe') === 'true';
          const isAuthOperation = req.url.includes('/auth/refresh') || req.url.includes('/auth/login');
          if (rememberMe && !isAuthOperation) {
            return authService.refresh().pipe(
              switchMap(() => {
                return next(req);
              }),
              catchError((refreshError) => {
                userService.currentUser.set(null);
                localStorage.removeItem('rememberMe');
                router.navigate(['/login']);

                errorMessage = 'Session expired. Please log in again.';
                snackBar.open(errorMessage, 'Close', {
                  duration: 5000,
                  horizontalPosition: 'end',
                  verticalPosition: 'bottom',
                  panelClass: ['error-snackbar']
                });
                return throwError(() => refreshError);
              })
            );
          } else {
            userService.currentUser.set(null);
            localStorage.removeItem('rememberMe');

            if (!isAuthOperation) {
              router.navigate(['/login']);
              errorMessage = 'Unauthorized. Please log in again.';
            } else {
              errorMessage = error.error?.message || 'Authentication failed.';
            }
          }
        } else if (error.status === 403) {
          errorMessage = 'Access forbidden.';
        } else if (error.status === 404) {
          errorMessage = 'Resource not found.';
        } else if (error.status === 500) {
          errorMessage = 'Server error. Please try again later.';
        } else if (error.status >= 400 && error.status < 500) {
          errorMessage = error.error?.message || `Client error: ${error.status}`;
        } else if (error.status >= 500) {
          errorMessage = error.error?.message || `Server error: ${error.status}`;
        }
      }

      if (error.status !== 401 || localStorage.getItem('rememberMe') !== 'true' || req.url.includes('/auth/refresh')) {
        snackBar.open(errorMessage, 'Close', {
          duration: 5000,
          horizontalPosition: 'end',
          verticalPosition: 'bottom',
          panelClass: ['error-snackbar']
        });
      }

      return throwError(() => error);
    })
  );
};
