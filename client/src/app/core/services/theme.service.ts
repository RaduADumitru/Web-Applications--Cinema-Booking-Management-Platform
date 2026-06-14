import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  isDarkMode = signal<boolean>(false);

  initTheme() {
    const savedTheme = localStorage.getItem('app-theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    
    this.setTheme(savedTheme === 'dark' || (!savedTheme && prefersDark));
  }

  toggleTheme() {
    this.setTheme(!this.isDarkMode());
  }

  private setTheme(dark: boolean) {
    this.isDarkMode.set(dark);
    const rootElement = document.documentElement;

    if (dark) {
      rootElement.classList.add('theme-dark');
      localStorage.setItem('app-theme', 'dark');
    } else {
      rootElement.classList.remove('theme-dark');
      localStorage.setItem('app-theme', 'light');
    }
  }
}