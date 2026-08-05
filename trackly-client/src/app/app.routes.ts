import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'board',
    title: 'Trackly board',
    loadComponent: () => import('./board/board-page').then((m) => m.BoardPage),
  },
  { path: '', pathMatch: 'full', redirectTo: 'board' },
  { path: '**', redirectTo: 'board' },
];
