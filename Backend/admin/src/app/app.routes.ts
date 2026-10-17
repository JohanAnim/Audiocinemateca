import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { LoginComponent } from './features/login/login';
import { AdminLayoutComponent } from './features/layout/admin-layout';
import { DashboardComponent } from './features/dashboard/dashboard';
import { CatalogListComponent } from './features/catalog/catalog-list';
import { CatalogCreateComponent } from './features/catalog/catalog-create';
import { UsersListComponent } from './features/users/users-list';
import { CommunityChatComponent } from './features/community/community-chat';
import { ConfigEditorComponent } from './features/config/config-editor';
import { AgentsConfigComponent } from './features/agents/agents-config';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent,
    title: 'Iniciar Sesión | Audiocinemateca Admin',
  },
  {
    path: '',
    component: AdminLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        component: DashboardComponent,
        title: 'Dashboard & Métricas | Audiocinemateca Admin',
      },
      {
        path: 'catalog',
        component: CatalogListComponent,
        title: 'Catálogo de Contenidos | Audiocinemateca Admin',
      },
      {
        path: 'catalog/create',
        component: CatalogCreateComponent,
        title: 'Nueva Obra Exclusiva | Audiocinemateca Admin',
      },
      {
        path: 'catalog/edit/:id',
        component: CatalogCreateComponent,
        title: 'Editar Obra | Audiocinemateca Admin',
      },
      {
        path: 'users',
        component: UsersListComponent,
        title: 'Usuarios & Sanciones | Audiocinemateca Admin',
      },
      {
        path: 'community',
        component: CommunityChatComponent,
        title: 'Comunidad & Chat en Vivo | Audiocinemateca Admin',
      },
      {
        path: 'config',
        component: ConfigEditorComponent,
        title: 'Configuración Remota & APK | Audiocinemateca Admin',
      },
      {
        path: 'agents',
        component: AgentsConfigComponent,
        title: 'Aura IA & Automatizaciones | Audiocinemateca Admin',
      },
      {
        path: 'aura-chat',
        loadComponent: () =>
          import('./features/agents/aura-chat-panel').then((m) => m.AuraChatPanelComponent),
        title: 'Chat con Aura | Audiocinemateca Admin',
      },
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
