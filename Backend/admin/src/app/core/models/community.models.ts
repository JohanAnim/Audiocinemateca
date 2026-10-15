export interface ChatMessageUser {
  id: string;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: 'USER' | 'MODERATOR' | 'ADMIN';
}

export interface ChatMessage {
  id: string;
  userId: string;
  content: string;
  isPinned: boolean;
  createdAt: string;
  user: ChatMessageUser;
}

export interface OnlineUser {
  id: string;
  userId: string;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: 'USER' | 'MODERATOR' | 'ADMIN';
  deviceInfo: string | null;
  currentlyListening: string | null;
  online: boolean;
}

export interface Announcement {
  id: string;
  authorId: string;
  title: string | null;
  content: string;
  createdAt: string;
  author?: {
    id: string;
    username: string;
    displayName: string | null;
  };
}
