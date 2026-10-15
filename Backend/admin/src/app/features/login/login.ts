import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-wrapper">
      <div class="login-card">
        <div class="logo-area">
          <div class="logo-icon">🎬</div>
          <h1>Audiocinemateca</h1>
          <p class="subtitle">Panel de Control & Consola Administrativa</p>
        </div>

        @if (errorMessage()) {
          <div class="alert-error" role="alert">
            <span class="alert-icon">⚠️</span>
            <span>{{ errorMessage() }}</span>
          </div>
        }

        <form (ngSubmit)="onSubmit()" #loginForm="ngForm">
          <div class="form-group">
            <label class="form-label" for="identifier">Usuario o Correo</label>
            <input
              id="identifier"
              name="identifier"
              type="text"
              class="form-control"
              [(ngModel)]="identifier"
              required
              placeholder="admin o tu@correo.com"
              autocomplete="username"
              [disabled]="isLoading()"
            />
          </div>

          <div class="form-group">
            <label class="form-label" for="password">Contraseña</label>
            <div class="password-input-group">
              <input
                id="password"
                name="password"
                [type]="showPassword() ? 'text' : 'password'"
                class="form-control"
                [(ngModel)]="password"
                required
                placeholder="••••••••••••"
                autocomplete="current-password"
                [disabled]="isLoading()"
              />
              <button
                type="button"
                class="btn-toggle-pw"
                (click)="showPassword.set(!showPassword())"
                tabindex="-1"
                aria-label="Alternar visibilidad de contraseña"
              >
                {{ showPassword() ? '👁️' : '🔒' }}
              </button>
            </div>
          </div>

          <button
            type="submit"
            class="btn btn-primary btn-block"
            [disabled]="isLoading() || !identifier || !password"
          >
            @if (isLoading()) {
              <span class="spinner"></span>
              <span>Iniciando sesión segura...</span>
            } @else {
              <span>Acceder al Panel de Control</span>
              <span>→</span>
            }
          </button>
        </form>

        <div class="login-footer">
          <p>Exclusivo para administradores de Audiocinemateca.</p>
          <span class="badge badge-info">v3.1.0 • Angular 22</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-wrapper {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: radial-gradient(circle at top center, #1e1b4b 0%, #080c14 70%);
    }

    .login-card {
      width: 100%;
      max-width: 440px;
      background-color: rgba(18, 24, 38, 0.95);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-xl);
      padding: 36px 32px;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(12px);
    }

    .logo-area {
      text-align: center;
      margin-bottom: 28px;
    }

    .logo-icon {
      font-size: 42px;
      display: inline-block;
      margin-bottom: 12px;
    }

    h1 {
      font-size: 24px;
      font-weight: 800;
      color: var(--text-main);
      letter-spacing: -0.5px;
    }

    .subtitle {
      font-size: 14px;
      color: var(--text-muted);
      margin-top: 4px;
    }

    .alert-error {
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: #fca5a5;
      padding: 12px 14px;
      border-radius: var(--radius-sm);
      font-size: 13px;
      margin-bottom: 20px;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .password-input-group {
      position: relative;
    }

    .btn-toggle-pw {
      position: absolute;
      right: 12px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      font-size: 16px;
      opacity: 0.7;
    }

    .btn-toggle-pw:hover {
      opacity: 1;
    }

    .btn-block {
      width: 100%;
      padding: 14px;
      font-size: 15px;
      margin-top: 8px;
    }

    .login-footer {
      text-align: center;
      margin-top: 28px;
      padding-top: 20px;
      border-top: 1px solid var(--border-color);
      font-size: 12px;
      color: var(--text-dim);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
    }

    .spinner {
      width: 16px;
      height: 16px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `],
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  identifier = '';
  password = '';
  showPassword = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  isLoading = this.auth.isLoading;

  onSubmit() {
    if (!this.identifier || !this.password) return;

    this.errorMessage.set(null);
    this.auth.login(this.identifier, this.password).subscribe({
      next: () => {
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        const msg =
          err?.error?.message ||
          err?.message ||
          'Credenciales inválidas o error de conexión con el servidor.';
        this.errorMessage.set(Array.isArray(msg) ? msg.join(', ') : msg);
      },
    });
  }
}
