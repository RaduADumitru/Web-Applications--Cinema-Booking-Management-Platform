import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-staff-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './staff-dashboard.html',
  styleUrl: './staff-dashboard.css',
})
export class StaffDashboardComponent {
  readonly panels = [
    { label: 'Rooms', path: 'rooms', meta: 'Create rooms and attach existing assets' },
    { label: 'Seats', path: 'seats', meta: 'Generate coordinate seating blueprints' },
    { label: 'Showtimes', path: 'screen-sessions', meta: 'Schedule movies into rooms' },
    { label: 'Tickets', path: 'tickets', meta: 'Generate purchasable seat inventory' },
    { label: 'Prices', path: 'prices', meta: 'Set ticket category prices' },
  ];
}
