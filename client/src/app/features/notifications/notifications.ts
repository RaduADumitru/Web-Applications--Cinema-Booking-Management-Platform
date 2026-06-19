import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { NotificationsService } from '@services/notifications.service';
import { UserService } from '@services/user.service';
import { NotificationResponse } from '@models/notifications.models';
import { computed, signal } from '@angular/core';
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.html',
  styleUrl: './notifications.css'
})
export class Notifications implements OnInit {
  private readonly notificationsService = inject(NotificationsService);
  public readonly userService = inject(UserService);

  notifications = signal<NotificationResponse[]>([]);
  isOpen = signal(false);

  unreadCount = computed(() => 
    this.notifications().filter(n => !n.sentDate).length
  );

  ngOnInit() {
    if (this.userService.isAuthenticated()) {
      this.loadNotifications();
    }
  }

  loadNotifications() {
    this.notificationsService.getNotifications().subscribe({
      next: (data) => this.notifications.set(data),
      error: () => this.notifications.set([])
    });
  }

  toggleDropdown() {
    this.isOpen.update(state => !state);
  }

  closeDropdown() {
    this.isOpen.set(false);
  }

  markAsRead(id: number, event: Event) {
    event.stopPropagation(); 
    this.notificationsService.markAsRead(id).subscribe({
      next: () => {
        this.notifications.update(items =>
          items.map(n => n.id === id ? { ...n, sentDate: new Date() } : n)
        );
      }
    });
  }
}