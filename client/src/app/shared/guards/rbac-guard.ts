import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { UserService } from '@services/user.service';

export const rbacGuard = (allowedRole: string): CanActivateFn => {
  return () => {
    const userService = inject(UserService);
    const router = inject(Router);

    if (userService.hasRole(allowedRole)) {
      return true;
    }

    return router.createUrlTree(['/home']);
  };
};