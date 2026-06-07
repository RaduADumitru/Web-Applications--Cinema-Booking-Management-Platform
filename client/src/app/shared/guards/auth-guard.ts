import { inject } from '@angular/core/primitives/di';
import { CanActivateFn, Router } from '@angular/router';
import { UserService } from '@services/user.service';
import { map } from 'rxjs/internal/operators/map';

export const authGuard: CanActivateFn = () => {
  const userService = inject(UserService);
  const router = inject(Router);

  if (userService.currentUser()) {
    return true;
  }

  return userService.loadUserProfile().pipe(
    map(profile => {
      if (profile) {
        return true;
      }
      
      return router.createUrlTree(['/login']);
    })
  );
};
