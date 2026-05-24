import { Routes } from '@angular/router';
import { RegisterComponent } from '@features/auth/register/register.component';
import { LoginComponent } from '@features/auth/login/login.component';
import { HomeComponent } from '@features/home/home';
import { AdminComponent } from '@features/admin/admin';
import { authGuard } from './core/auth/auth-guard';

export const routes: Routes = [
    { path: '', redirectTo: 'home', pathMatch: 'full' },
    { path: 'home', component: HomeComponent, canMatch: [authGuard] },
    { path: 'login', component: LoginComponent, canMatch: [authGuard] },
    { path: 'register', component: RegisterComponent, canMatch: [authGuard] },
    { path: 'admin', component: AdminComponent, canMatch: [authGuard] },
    { path: '**', redirectTo: 'home' }
];
