import { Routes } from '@angular/router';
import { RegisterComponent } from '@features/auth/register/register.component';
import { LoginComponent } from './features/auth/login/login.component';
import { HomeComponent } from '@features/home/home';
import { AdminComponent } from '@features/admin/admin';
import { authGuard } from '@guards/auth-guard';
import { guestGuard } from '@guards/guest-guard';
import { rbacGuard } from '@guards/rbac-guard';

export const routes: Routes = [
    { path: '', redirectTo: 'home', pathMatch: 'full' },
    { path: 'home', component: HomeComponent, canMatch: [authGuard] },
    { path: 'login', component: LoginComponent, canMatch: [guestGuard] },
    { path: 'register', component: RegisterComponent, canMatch: [guestGuard] },
    { path: 'admin', component: AdminComponent, canMatch: [authGuard, rbacGuard('ADMIN')] },
    { path: '**', redirectTo: 'home' }
];
