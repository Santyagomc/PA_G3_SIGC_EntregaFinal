import { Routes } from '@angular/router';

import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'inicio' },
  {
    path: 'inicio',
    loadComponent: () => import('./pages/landing/landing').then((m) => m.LandingPage),
  },
  {
    path: 'asistente',
    loadComponent: () => import('./pages/chatbot/chatbot').then((m) => m.ChatbotPage),
    data: { publicAssistant: true },
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register/register').then((m) => m.RegisterPage),
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./layout/dashboard-shell/dashboard-shell').then((m) => m.DashboardShell),
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/dashboard-home/dashboard-home').then((m) => m.DashboardHome),
      },
      {
        path: 'solicitudes',
        loadComponent: () =>
          import('./pages/solicitudes/solicitudes').then((m) => m.SolicitudesPage),
      },
      {
        path: 'documentos',
        loadComponent: () => import('./pages/documentos/documentos').then((m) => m.DocumentosPage),
      },
      {
        path: 'usuarios',
        canActivate: [adminGuard],
        loadComponent: () => import('./pages/usuarios/usuarios').then((m) => m.UsuariosPage),
      },
      {
        path: 'chatbot',
        loadComponent: () => import('./pages/chatbot/chatbot').then((m) => m.ChatbotPage),
      },
      {
        path: 'configuracion',
        loadComponent: () =>
          import('./pages/configuracion/configuracion').then((m) => m.ConfiguracionPage),
      },
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];
