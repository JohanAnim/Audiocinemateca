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
    <main class="login-wrapper" role="main" aria-labelledby="login-title">
      <div class="login-card">
        <header class="logo-area">
          <div class="logo-icon" aria-hidden="true">🎬</div>
          <h1 id="login-title">Audiocinemateca</h1>
          <p class="subtitle">Consola de Administración y Gestión Soberana</p>
          <div class="wcag-tag" title="Interfaz accesible de conformidad estricta">
            <span aria-hidden="true">♿</span> Conforme a WCAG 2.1 AAA
          </div>
        </header>

        @if (errorMessage()) {
          <div class="alert-error" role="alert" aria-live="assertive">
            <span class="alert-icon" aria-hidden="true">⚠️</span>
            <span>{{ errorMessage() }}</span>
          </div>
        }

        <form (ngSubmit)="onSubmit()" #loginForm="ngForm" aria-label="Formulario de autenticación administrativa">
          <div class="form-group">
            <label class="form-label" for="identifier">
              Usuario o Correo Electrónico
              <span class="required-indicator" aria-hidden="true">*</span>
            </label>
            <input
              id="identifier"
              name="identifier"
              type="text"
              class="form-control"
              [(ngModel)]="identifier"
              required
              aria-required="true"
              placeholder="Johan-a-g o tu@correo.com"
              autocomplete="username"
              [disabled]="isLoading()"
              aria-describedby="identifier-hint"
            />
            <span id="identifier-hint" class="field-hint">
              Ingresa tu nombre de usuario administrativo o tu correo registrado.
            </span>
          </div>

          <div class="form-group">
            <label class="form-label" for="password">
              Contraseña de Acceso
              <span class="required-indicator" aria-hidden="true">*</span>
            </label>
            <div class="password-input-group">
              <input
                id="password"
                name="password"
                [type]="showPassword() ? 'text' : 'password'"
                class="form-control"
                [(ngModel)]="password"
                required
                aria-required="true"
                placeholder="Ingresa tu contraseña"
                autocomplete="current-password"
                [disabled]="isLoading()"
              />
              <button
                type="button"
                class="btn-toggle-pw cursor-pointer"
                (click)="showPassword.set(!showPassword())"
                [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Ver contraseña en texto plano'"
              >
                <span aria-hidden="true">{{ showPassword() ? '👁️' : '🔒' }}</span>
              </button>
            </div>
          </div>

          <button
            type="submit"
            class="btn btn-primary btn-block cursor-pointer"
            [disabled]="isLoading() || !identifier || !password"
            [attr.aria-busy]="isLoading()"
          >
            @if (isLoading()) {
              <span class="spinner" aria-hidden="true"></span>
              <span>Validando credenciales seguras...</span>
            } @else {
              <span>Ingresar a la Consola Administrativa</span>
              <span aria-hidden="true">→</span>
            }
          </button>
        </form>

        <footer class="login-footer">
          <p>Exclusivo para administradores y moderadores autorizados de Audiocinemateca.</p>
          <div class="footer-pills">
            <span class="badge badge-primary">Backend Soberano v3.1.0</span>
            <span class="badge badge-success">PostgreSQL 16</span>
          </div>
        </footer>
      </div>
    </main>
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
      max-width: 460px;
      background-color: rgba(18, 24, 38, 0.95);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-xl);
      padding: 38px 34px;
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
      font-size: 26px;
      font-weight: 800;
      color: var(--text-main);
      letter-spacing: -0.5px;
    }

    .subtitle {
      font-size: 14px;
      color: var(--text-muted);
      margin-top: 4px;
      line-height: 1.4;
    }

    .wcag-tag {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      margin-top: 10px;
      font-size: 11px;
      font-weight: 700;
      color: #38bdf8;
      background: rgba(56, 189, 248, 0.1);
      border: 1px solid rgba(56, 189, 248, 0.25);
      padding: 3px 10px;
      border-radius: 20px;
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

    .required-indicator {
      color: #f87171;
      margin-left: 2px;
    }

    .field-hint {
      display: block;
      font-size: 11px;
      color: var(--text-dim);
      margin-top: 6px;
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
      padding: 4px 6px;
      opacity: 0.8;
      border-radius: 4px;
    }

    .btn-toggle-pw:hover, .btn-toggle-pw:focus {
      opacity: 1;
    }

    .btn-block {
      width: 100%;
      padding: 14px;
      font-size: 15px;
      margin-top: 12px;
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
      gap: 12px;
    }

    .footer-pills {
      display: flex;
      gap: 8px;
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
