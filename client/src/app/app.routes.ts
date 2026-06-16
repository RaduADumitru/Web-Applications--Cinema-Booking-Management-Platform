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
        path: 'users',
        loadComponent: () => import('@features/users/users').then(m => m.UsersComponent),
        canActivate: [authGuard, rbacGuard('OWNER')]
      },
      {
        path: 'staff',
        loadComponent: () => import('@features/staff/staff-dashboard/staff-dashboard').then(m => m.StaffDashboardComponent),
        canActivate: [authGuard, rbacGuard('STAFF')],
        children: [
          { path: '', redirectTo: 'rooms', pathMatch: 'full' },
          {
            path: 'rooms',
            loadComponent: () => import('@features/staff/room-layout-manager/room-layout-manager').then(m => m.RoomLayoutManagerComponent)
          },
          {
            path: 'seats',
            loadComponent: () => import('@features/staff/seat-blueprint/seat-blueprint').then(m => m.SeatBlueprintComponent)
          },
          {
            path: 'screen-sessions',
            loadComponent: () => import('@features/staff/screen-session-manager/screen-session-manager').then(m => m.ScreenSessionManagerComponent)
          },
          {
            path: 'tickets',
            loadComponent: () => import('@features/staff/ticket-allocation/ticket-allocation').then(m => m.TicketAllocationComponent)
          },
          {
            path: 'prices',
            loadComponent: () => import('@features/staff/ticket-pricing/ticket-pricing').then(m => m.TicketPricingComponent)
          }
        ]
      },
      {
        path: 'seat-selection',
        loadComponent: () => import('@features/seat-selection/seat-selection').then(m => m.SeatSelectionComponent),
        canActivate: [authGuard]
      },
      {
        path: 'tickets',
        loadComponent: () => import('@features/tickets/tickets').then(m => m.TicketsComponent),
        canActivate: [authGuard]
      },
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
