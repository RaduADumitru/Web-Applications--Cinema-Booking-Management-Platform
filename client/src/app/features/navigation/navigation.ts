import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { UserService } from '@services/user.service';
import { ThemeService } from '@services/theme.service';
import { AuthService } from '@services/auth.service';
import { Notifications } from '../notifications/notifications';
import { CartComponent } from '../cart/cart';

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, Notifications, CartComponent],
  templateUrl: './navigation.html',
  styleUrls: []
})
export class NavigationComponent {
  public readonly userService = inject(UserService);
  public readonly themeService = inject(ThemeService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  isMobileMenuOpen = signal<boolean>(false);

  toggleMobileMenu(): void {
    this.isMobileMenuOpen.update(state => !state);
  }

  closeMobileMenu(): void {
    this.isMobileMenuOpen.set(false);
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.clearLocalSession(),
      error: () => this.clearLocalSession()
    });
  }

  private clearLocalSession(): void {
    this.userService.currentUser.set(null);
    localStorage.removeItem('rememberMe');
    this.closeMobileMenu();
    this.router.navigate(['/login']);
  }
}