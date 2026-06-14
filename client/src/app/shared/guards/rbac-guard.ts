import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { UserService } from '@services/user.service';

export const RoleHierarchy: Record<string, number> = {
  USER: 1,
  STAFF: 2,
  OWNER: 3,
};

export const rbacGuard = (minimumRole: string): CanActivateFn => {
  return () => {
    const userService = inject(UserService);
    const router = inject(Router);
    
    const currentUserRole = userService.getRole();
    
    const currentUserWeight = RoleHierarchy[currentUserRole] || 0;
    const requiredWeight = RoleHierarchy[minimumRole] || 0;

    if (currentUserWeight >= requiredWeight) {
      return true;
    }

    return router.createUrlTree(['/home']);
  };
};