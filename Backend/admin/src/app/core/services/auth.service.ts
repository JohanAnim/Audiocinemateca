import { Injectable, inject, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { tap, catchError, of } from 'rxjs';
import { ApiService } from './api.service';
import { AdminUser, AuthResponse } from '../models/auth.models';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly currentUser = signal<AdminUser | null>(null);
  readonly isLoading = signal<boolean>(false);

  readonly isAuthenticated = computed(() => !!this.currentUser());
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');
  readonly isModerator = computed(() => {
    const role = this.currentUser()?.role;
    return role === 'ADMIN' || role === 'MODERATOR';
  });

  constructor() {
    this.checkInitialSession();
  }

  private checkInitialSession() {
    if (typeof localStorage === 'undefined') return;

    const token = localStorage.getItem('audiocinemateca_admin_token');
    const savedUser = localStorage.getItem('audiocinemateca_admin_user');

    if (token && savedUser) {
      try {
        const user = JSON.parse(savedUser) as AdminUser;
        if (user.role === 'ADMIN' || user.role === 'MODERATOR') {
          this.currentUser.set(user);
          // Verificar sesión fresca en segundo plano
          this.refreshProfile();
        } else {
          this.logout();
        }
      } catch {
        this.logout();
      }
    }
  }

  login(identifier: string, password: string) {
    this.isLoading.set(true);
    return this.api
      .post<AuthResponse>('auth/login', { identifier, password })
      .pipe(
        tap((res) => {
          this.isLoading.set(false);
          const user = res.user;

          if (user.role !== 'ADMIN' && user.role !== 'MODERATOR') {
            throw new Error('Acceso restringido: Se requieren permisos de Administrador');
          }

          if (typeof localStorage !== 'undefined') {
            localStorage.setItem('audiocinemateca_admin_token', res.tokens.accessToken);
            localStorage.setItem('audiocinemateca_admin_refresh', res.tokens.refreshToken);
            localStorage.setItem('audiocinemateca_admin_user', JSON.stringify(user));
          }

          this.currentUser.set(user);
        }),
        catchError((err) => {
          this.isLoading.set(false);
          throw err;
        }),
      );
  }

  refreshProfile() {
    return this.api.get<AdminUser>('auth/me').pipe(
      tap((user) => {
        if (user.role === 'ADMIN' || user.role === 'MODERATOR') {
          this.currentUser.set(user);
          localStorage.setItem('audiocinemateca_admin_user', JSON.stringify(user));
        } else {
          this.logout();
        }
      }),
      catchError(() => {
        this.logout();
        return of(null);
      }),
    ).subscribe();
  }

  logout() {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('audiocinemateca_admin_token');
      localStorage.removeItem('audiocinemateca_admin_refresh');
      localStorage.removeItem('audiocinemateca_admin_user');
    }
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }
}
