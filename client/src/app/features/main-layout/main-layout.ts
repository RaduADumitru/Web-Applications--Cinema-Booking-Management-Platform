import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavigationComponent } from '../navigation/navigation';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, NavigationComponent],
  template: `
    <app-navigation></app-navigation>
    
    <main>
      <router-outlet></router-outlet>
    </main>
  `
})
export class MainLayoutComponent {}