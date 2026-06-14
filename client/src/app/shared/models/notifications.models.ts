import { OrderResponse } from './order.models';

export enum NotificationType {
  MOVIE_REMINDER = 'MOVIE_REMINDER',
  REGISTERED_FROM_ANOTHER_DEVICE = 'REGISTERED_FROM_ANOTHER_DEVICE',
  EMAIL_VERIFICATION = 'EMAIL_VERIFICATION',
  TICKET_BOUGHT = 'TICKET_BOUGHT',
  SUCCESSFUL_PAYMENT = 'SUCCESSFUL_PAYMENT'
}

export interface NotificationResponse {
  id: number; 
  type: NotificationType;
  content: string; 
  createdDate: Date; 
  sentDate?: Date | null; 
  userId: string; 
  order?: OrderResponse | null;
}

export interface CreateNotificationRequest {
  type: NotificationType;
  content: string;
  userId: string;
}