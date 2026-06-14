import { Routes } from '@angular/router';
import { RegisterComponent } from '@features/auth/register/register.component';
import { LoginComponent } from '@features/auth/login/login.component';
import { HomeComponent } from '@features/home/home';
import { AdminComponent } from '@features/admin/admin';
import { authGuard } from '@guards/auth-guard';
import { guestGuard } from '@guards/guest-guard';
import { rbacGuard } from '@guards/rbac-guard';

import { MainLayoutComponent } from '@features/main-layout/main-layout';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'register', component: RegisterComponent, canActivate: [guestGuard] },

  {
    path: '',
    component: MainLayoutComponent,
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      { path: 'home', component: HomeComponent, canActivate: [authGuard] },
      { path: 'admin', component: AdminComponent, canActivate: [authGuard, rbacGuard('OWNER')] },
      { 
        path: 'profile', 
        loadComponent: () => import('@features/user-profile/user-profile-component').then(m => m.UserProfileComponent), 
        canActivate: [authGuard] 
      }
    ]
  },

  // 3. Fallback Catch-All
  { path: '**', redirectTo: 'home' }
];