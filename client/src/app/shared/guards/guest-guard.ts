import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { UserService } from '@services/user.service' 


export const guestGuard: CanMatchFn = () => {
  const userService = inject(UserService);
  const router = inject(Router);

  if (!userService.isAuthenticated()) {
    return true;
  }
  
  return router.createUrlTree(['/home']);
};