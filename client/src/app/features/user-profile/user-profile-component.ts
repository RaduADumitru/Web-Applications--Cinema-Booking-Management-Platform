import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService } from '@services/user.service';
import { UpdateProfileRequest } from '@app/shared/models/user.models';
import { ThemeService } from '@app/core/services/theme.service';

@Component({
  selector: 'app-user-profile-component',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-profile-component.html',
  styleUrl: './user-profile-component.css',
})
export class UserProfileComponent implements OnInit {
  protected userService = inject(UserService);
  protected themeService = inject(ThemeService);

  isLoading = signal<boolean>(false);
  isEditing = signal<boolean>(false);
  error = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  editForm = {
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: ''
  };

  ngOnInit(): void {
    this.fetchProfile();
  }

  fetchProfile(): void {
    this.isLoading.set(true);
    this.error.set(null);

    this.userService.loadUserProfile().subscribe({
      next: (profile) => {
        this.isLoading.set(false);
        if (profile) {
          this.syncFormValues(profile);
        } else {
          this.error.set('Could not parse user profile data.');
        }
      },
      error: () => {
        this.isLoading.set(false);
        this.error.set('Failed to communicate with the server to retrieve profile details.');
      }
    });
  }

  private syncFormValues(profile: any): void {
    this.editForm = {
      firstName: profile.firstName || '',
      lastName: profile.lastName || '',
      email: profile.email || '',
      phoneNumber: profile.phoneNumber || ''
    };
  }

  startEditing(): void {
    const profile = this.userService.currentUser();
    if (profile) {
      this.syncFormValues(profile);
      this.isEditing.set(true);
      this.clearStatusAlerts();
    }
  }

  cancelEditing(): void {
    this.isEditing.set(false);
    this.clearStatusAlerts();
  }

  saveProfile(): void {
    this.isLoading.set(true);
    this.clearStatusAlerts();

    const updateData: UpdateProfileRequest = {
      firstName: this.editForm.firstName,
      lastName: this.editForm.lastName,
      email: this.editForm.email,
      phoneNumber: this.editForm.phoneNumber
    };

    this.userService.updateProfile(updateData).subscribe({
      next: (updatedProfile) => {
        this.userService.currentUser.set(updatedProfile);
        this.isEditing.set(false);
        this.isLoading.set(false);
        this.successMessage.set('Profile information saved successfully.');
      },
      error: () => {
        this.isLoading.set(false);
        this.error.set('An unexpected problem occurred while updating your information.');
      }
    });
  }

  deleteAccount(): void {
    const confirmation = confirm('Are you sure you want to permanently delete your account? This action cannot be reversed.');
    if (!confirmation) return;

    this.isLoading.set(true);
    this.clearStatusAlerts();

    this.userService.deleteOwnAccount().subscribe({
      next: (response) => {
        this.isLoading.set(false);
        this.userService.currentUser.set(null);
        alert(response.message || 'Your account has been deleted.');
        // Optional logic: Route user back to landing/home stage here
      },
      error: () => {
        this.isLoading.set(false);
        this.error.set('Failed to delete account. Please try again later.');
      }
    });
  }

  private clearStatusAlerts(): void {
    this.error.set(null);
    this.successMessage.set(null);
  }
}