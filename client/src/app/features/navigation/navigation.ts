import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { UserService } from '@services/user.service';
import { ThemeService } from '@services/theme.service'; 
import { Notifications } from '../notifications/notifications';

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, Notifications],
  templateUrl: './navigation.html',
  styleUrls: [] 
})
export class NavigationComponent {
  public readonly userService = inject(UserService);
  public readonly themeService = inject(ThemeService);

  isMobileMenuOpen = signal<boolean>(false);

  toggleMobileMenu(): void {
    this.isMobileMenuOpen.update(state => !state);
  }

  closeMobileMenu(): void {
    this.isMobileMenuOpen.set(false);
  }
}