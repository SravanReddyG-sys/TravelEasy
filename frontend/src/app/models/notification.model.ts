export interface InAppNotification {
  id: number;
  eventId: string;
  recipientUserId: number;
  recipientEmail: string;
  recipientRole: 'TRAVELLER' | 'BUS_OPERATOR' | 'HOTEL_PARTNER' | 'ADMIN' | string;
  eventType: string;
  title: string;
  message: string;
  actionUrl?: string | null;
  metadataJson?: string | null;
  isRead: boolean;
  readAt?: string | null;
  createdAt: string;
  expiresAt?: string | null;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface MarkAllReadResponse {
  status: string;
  markedRead: number;
}

export interface FormattedNotification extends InAppNotification {
  timeAgo: string;
  iconClass: string;
  iconColorClass: string;
  badgeClass: string;
}
