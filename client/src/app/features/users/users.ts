import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserService } from '@services/user.service';
import { ProfileResponse } from '@models/user.models';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './users.html',
  styleUrls: ['./users.css']
})
export class UsersComponent implements OnInit {
  public readonly userService = inject(UserService);

  users = signal<ProfileResponse[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  currentPage = signal(0);
  pageSize = 8;
  totalPages = signal(0);
  totalElements = signal(0);
  pageNumbers = signal<number[]>([]);

  ngOnInit(): void {
    this.loadUsers(0);
  }

  loadUsers(page: number): void {
    this.loading.set(true);
    this.error.set(null);

    this.userService.getUsersPage(page + 1, this.pageSize).subscribe({
      next: (response) => {
        this.users.set(response.content);
        this.currentPage.set(response.page.number);
        this.totalPages.set(response.page.totalPages);
        this.totalElements.set(response.page.totalElements);
        this.updatePageNumbers();
      },
      error: (err) => {
        console.error('Failed to load users:', err);
        this.error.set('Unable to load users. Please verify permissions and try again.');
      }
    }).add(() => {
      this.loading.set(false);
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.currentPage()) {
      return;
    }
    this.loadUsers(page);
  }

  updatePageNumbers(): void {
    const maxVisiblePages = 5;
    const pages: number[] = [];
    const current = this.currentPage();
    const total = this.totalPages();

    let startPage = Math.max(0, current - Math.floor(maxVisiblePages / 2));
    let endPage = startPage + maxVisiblePages - 1;

    if (endPage >= total) {
      endPage = total - 1;
      startPage = Math.max(0, endPage - maxVisiblePages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      pages.push(i);
    }

    this.pageNumbers.set(pages);
  }

  isCurrentUser(user: ProfileResponse): boolean {
    const current = this.userService.currentUser();
    return current ? current.username === user.username : false;
  }

  changeRole(user: ProfileResponse, event: Event): void {
    const newRole = (event.target as HTMLSelectElement).value as 'OWNER' | 'STAFF' | 'USER';
    if (newRole === user.role) return;

    if (this.isCurrentUser(user)) {
      Swal.fire({
        icon: 'error',
        title: 'Access Denied',
        text: 'You cannot change your own role.',
        confirmButtonText: 'OK',
        customClass: { popup: 'swal-bg' }
      });
      this.loadUsers(this.currentPage());
      return;
    }

    Swal.fire({
      title: 'Change User Role?',
      text: `Are you sure you want to change ${user.firstName} ${user.lastName}'s role from ${user.role} to ${newRole}?`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Yes, change it',
      cancelButtonText: 'Cancel',
      customClass: { popup: 'swal-bg' }
    }).then((result) => {
      if (result.isConfirmed) {
        this.userService.promoteUser({ id: user.id, role: newRole }).subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: 'Role Updated',
              text: `${user.firstName} ${user.lastName} has been changed to ${newRole}.`,
              confirmButtonText: 'OK',
              customClass: { popup: 'swal-bg' }
            });
            this.loadUsers(this.currentPage());
          },
          error: (err) => {
            Swal.fire({
              icon: 'error',
              title: 'Update Failed',
              text: err.error?.message || 'An error occurred while updating the role. Please try again.',
              confirmButtonText: 'OK',
              customClass: { popup: 'swal-bg' }
            });
            this.loadUsers(this.currentPage());
          }
        });
      } else {
        this.loadUsers(this.currentPage());
      }
    });
  }

  deleteUser(user: ProfileResponse): void {
    if (this.isCurrentUser(user)) {
      Swal.fire({
        icon: 'error',
        title: 'Action Blocked',
        text: 'You cannot delete your own account.',
        confirmButtonText: 'OK',
        customClass: { popup: 'swal-bg' }
      });
      return;
    }

    Swal.fire({
      title: 'Delete User Account?',
      text: `Are you sure you want to delete ${user.firstName} ${user.lastName}'s account? This action is permanent and cannot be undone.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: 'var(--error, #dc2626)',
      cancelButtonColor: 'var(--border, #d1d5db)',
      confirmButtonText: 'Yes, delete account',
      cancelButtonText: 'Cancel',
      customClass: { popup: 'swal-bg' }
    }).then((result) => {
      if (result.isConfirmed) {
        this.userService.deleteUserById(user.id).subscribe({
          next: (res) => {
            Swal.fire({
              icon: 'success',
              title: 'Account Deleted',
              text: res.message || 'The account has been deleted successfully.',
              confirmButtonText: 'OK',
              customClass: { popup: 'swal-bg' }
            });
            this.loadUsers(this.currentPage());
          },
          error: (err) => {
            Swal.fire({
              icon: 'error',
              title: 'Deletion Failed',
              text: err.error?.message || 'An error occurred while deleting the account. Please try again.',
              confirmButtonText: 'OK',
              customClass: { popup: 'swal-bg' }
            });
          }
        });
      }
    });
  }

  trackByUser(_: number, user: ProfileResponse): number {
    return user.id;
  }

  trackByPage(_: number, page: number): number {
    return page;
  }
}
