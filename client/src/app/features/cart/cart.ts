import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrdersService } from '@app/core/services/orders.service';
import { UserService } from '@services/user.service';
import { OrderResponse, OrderStatus } from '@app/shared/models/order.models';
import Swal from 'sweetalert2';

interface OrderDetails {
  movieTitle: string;
  showtimeDate: string;
  showtimeTime: string;
  format: string | null;
  loading: boolean;
}

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './cart.html',
  styleUrl: './cart.css'
})
export class CartComponent implements OnInit {
  public readonly ordersService = inject(OrdersService);
  public readonly userService = inject(UserService);

  isOpen = signal(false);
  orderDetailsMap = signal<Map<string, OrderDetails>>(new Map());

  payingOrderId = signal<number | null>(null);
  cancellingOrderId = signal<number | null>(null);

  ngOnInit() {
    if (this.userService.isAuthenticated()) {
      this.loadOrders();
    }
  }

  loadOrders() {
    this.ordersService.loadMyOrders().subscribe({
      next: (orders) => {
        orders.forEach(order => {
          if (order.status === OrderStatus.PENDING) {
            this.loadOrderDetails(order);
          }
        });
      }
    });
  }

  toggleDropdown() {
    this.isOpen.update(state => !state);
    if (this.isOpen()) {
      this.loadOrders();
    }
  }

  closeDropdown() {
    this.isOpen.set(false);
  }

  loadOrderDetails(order: OrderResponse) {
    if (this.orderDetailsMap().has(order.id) || !order.ticketIds || order.ticketIds.length === 0) {
      return;
    }

    const currentMap = new Map(this.orderDetailsMap());
    currentMap.set(order.id, {
      movieTitle: 'Loading ticket info...',
      showtimeDate: '',
      showtimeTime: '',
      format: '',
      loading: true
    });
    this.orderDetailsMap.set(currentMap);

    const firstTicketId = Number(order.ticketIds[0]);
    this.ordersService.getTicket(firstTicketId).subscribe({
      next: (ticket) => {
        if (ticket.screenSessionId) {
          this.ordersService.getScreenSession(ticket.screenSessionId).subscribe({
            next: (session) => {
              const updatedMap = new Map(this.orderDetailsMap());
              updatedMap.set(order.id, {
                movieTitle: session.movieTitle,
                showtimeDate: session.date,
                showtimeTime: session.startTime,
                format: session.format,
                loading: false
              });
              this.orderDetailsMap.set(updatedMap);
            },
            error: () => {
              const updatedMap = new Map(this.orderDetailsMap());
              updatedMap.set(order.id, {
                movieTitle: 'Session info unavailable',
                showtimeDate: '',
                showtimeTime: '',
                format: '',
                loading: false
              });
              this.orderDetailsMap.set(updatedMap);
            }
          });
        }
      },
      error: () => {
        const updatedMap = new Map(this.orderDetailsMap());
        updatedMap.set(order.id, {
          movieTitle: 'Ticket info unavailable',
          showtimeDate: '',
          showtimeTime: '',
          format: '',
          loading: false
        });
        this.orderDetailsMap.set(updatedMap);
      }
    });
  }

  payOrder(orderId: number, event: Event) {
    event.stopPropagation();
    this.payingOrderId.set(orderId);
    this.ordersService.payOrder(orderId).subscribe({
      next: () => {
        this.payingOrderId.set(null);
        Swal.fire({
          title: 'Success!',
          text: 'Payment successful! Enjoy your movie.',
          icon: 'success',
          toast: true,
          position: 'top-end',
          showConfirmButton: false,
          timer: 3000
        });
        this.userService.loadUserProfile().subscribe();
      },
      error: (err) => {
        this.payingOrderId.set(null);
        Swal.fire('Error', 'Payment failed. Please try again.', 'error');
        console.error('Payment error:', err);
      }
    });
  }

  cancelOrder(orderId: number, event: Event) {
    event.stopPropagation();
    
    Swal.fire({
      title: 'Are you sure?',
      text: 'Do you want to cancel this booking reservation?',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Yes, cancel it',
      cancelButtonText: 'No, keep it',
    }).then((result) => {
      if (result.isConfirmed) {
        this.cancellingOrderId.set(orderId);
        this.ordersService.cancelOrder(orderId).subscribe({
          next: () => {
            this.cancellingOrderId.set(null);
            Swal.fire({
              title: 'Cancelled!',
              text: 'Your reservation has been cancelled.',
              icon: 'success',
              toast: true,
              position: 'top-end',
              showConfirmButton: false,
              timer: 3000
            });
          },
          error: (err) => {
            this.cancellingOrderId.set(null);
            Swal.fire('Error', 'Cancellation failed.', 'error');
            console.error('Cancel error:', err);
          }
        });
      }
    });
  }
}
