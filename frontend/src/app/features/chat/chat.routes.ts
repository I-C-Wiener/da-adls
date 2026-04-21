import { Routes } from '@angular/router';

export const CHAT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./chat-layout/chat-layout.component').then(m => m.ChatLayoutComponent),
    children: [
      {
        path: 'room/:id',
        loadComponent: () => import('./message-area/message-area.component').then(m => m.MessageAreaComponent),
      },
      {
        path: 'dm/:userId',
        loadComponent: () => import('./message-area/message-area.component').then(m => m.MessageAreaComponent),
      },
      {
        path: 'browse',
        loadComponent: () => import('./browse-rooms/browse-rooms.component').then(m => m.BrowseRoomsComponent),
      },
      {
        path: 'room/:id/manage',
        loadComponent: () => import('../../features/rooms/manage-room/manage-room.component').then(m => m.ManageRoomComponent),
      },
    ],
  },
];
