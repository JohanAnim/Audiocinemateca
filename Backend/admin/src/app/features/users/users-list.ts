import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UsersService } from '../../core/services/users.service';
import { UserListItem } from '../../core/models/user.models';

@Component({
  selector: 'app-users-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="users-page">
      <div class="page-header">
        <div>
          <h1>Gestión de Usuarios & Moderación</h1>
          <p class="subtitle">Supervisa identidades, dispositivos activos, roles y aplica sanciones en caliente</p>
        </div>
      </div>

      <!-- FILTROS -->
      <div class="card filters-card">
        <div class="filters-row">
          <div class="search-box">
            <span class="search-icon">🔍</span>
            <input
              type="text"
              class="form-control"
              placeholder="Buscar por UID, usuario, correo o nombre..."
              [(ngModel)]="searchQuery"
              (keyup.enter)="applyFilters()"
            />
          </div>

          <select class="form-control role-select" [(ngModel)]="selectedRole" (change)="applyFilters()">
            <option value="">Todos los roles</option>
            <option value="USER">Usuarios (USER)</option>
            <option value="MODERATOR">Moderadores</option>
            <option value="ADMIN">Administradores</option>
          </select>

          <select class="form-control status-select" [(ngModel)]="selectedOnline" (change)="applyFilters()">
            <option value="">Cualquier estado</option>
            <option value="true">Solo en línea</option>
            <option value="false">Desconectados</option>
          </select>

          <button class="btn btn-secondary" (click)="applyFilters()">Filtrar</button>
        </div>
      </div>

      <!-- TABLA DE USUARIOS -->
      @if (isLoading()) {
        <div class="card loading-card">
          <div class="spinner"></div>
          <p>Cargando lista de usuarios...</p>
        </div>
      } @else if (users().length === 0) {
        <div class="card empty-card">
          <span>👥</span>
          <h3>No se encontraron usuarios</h3>
        </div>
      } @else {
        <div class="card table-card">
          <div class="table-responsive">
            <table class="table">
              <thead>
                <tr>
                  <th>Usuario & UID</th>
                  <th>Correo & Proveedor</th>
                  <th>Rol</th>
                  <th>Presencia & Dispositivo</th>
                  <th>Actividad</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                @for (user of users(); track user.id) {
                  <tr>
                    <td>
                      <div class="user-cell">
                        <div class="avatar" [class.online-border]="user.isOnline">
                          {{ (user.displayName || user.username).charAt(0).toUpperCase() }}
                        </div>
                        <div class="user-meta">
                          <strong class="user-name">{{ user.displayName || user.username }}</strong>
                          <span class="user-handle">&#64;{{ user.username }}</span>
                          <button class="uid-tag" (click)="copyUid(user.id)" title="Copiar UID de base de datos">
                            UID: {{ user.id.slice(0, 8) }}... 📋
                          </button>
                        </div>
                      </div>
                    </td>

                    <td>
                      <div class="email-meta">
                        <span>{{ user.email }}</span>
                        <div class="providers-row">
                          @for (acc of user.accounts; track acc.providerId) {
                            <span class="provider-pill">{{ acc.provider }}</span>
                          }
                        </div>
                      </div>
                    </td>

                    <td>
                      <span class="badge" [ngClass]="getRoleBadgeClass(user.role)">
                        {{ user.role }}
                      </span>
                    </td>

                    <td>
                      <div class="device-cell">
                        <div class="online-indicator">
                          <span class="dot" [class.online]="user.isOnline"></span>
                          <span>{{ user.isOnline ? 'En Línea' : 'Desconectado' }}</span>
                        </div>
                        <small class="device-name">📱 {{ user.deviceInfo || 'Android' }}</small>
                        @if (user.currentlyListening) {
                          <span class="listening-tag">🎧 {{ user.currentlyListening }}</span>
                        }
                      </div>
                    </td>

                    <td>
                      <div class="activity-meta">
                        <span>⭐ {{ user.stats.ratings }} calificaciones</span>
                        <span>❤️ {{ user.stats.favorites }} favoritos</span>
                        <span>💬 {{ user.stats.comments }} comentarios</span>
                      </div>
                    </td>

                    <td>
                      @if (user.isBanned) {
                        <span class="badge badge-danger" [title]="user.banReason || 'Suspendido'">
                          🚫 BANEADO
                        </span>
                      } @else {
                        <span class="badge badge-success">✓ ACTIVO</span>
                      }
                    </td>

                    <td>
                      <div class="actions-group">
                        <!-- CAMBIAR ROL -->
                        <button
                          class="btn btn-secondary btn-sm"
                          (click)="openRoleModal(user)"
                          title="Cambiar permisos y rol"
                        >
                          👑
                        </button>

                        <!-- BANEAR / DESBANEAR -->
                        @if (!user.isBanned) {
                          <button
                            class="btn btn-danger btn-sm"
                            (click)="openBanModal(user)"
                            title="Suspender o banear usuario"
                          >
                            🚫
                          </button>
                        } @else {
                          <button
                            class="btn btn-success btn-sm"
                            (click)="unbanUser(user)"
                            title="Levantar sanción y desbanear"
                          >
                            ✅
                          </button>
                        }

                        <!-- ELIMINAR -->
                        <button
                          class="btn btn-secondary btn-sm btn-delete"
                          (click)="confirmDelete(user)"
                          title="Eliminar usuario definitivamente"
                        >
                          🗑️
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- PAGINACIÓN -->
          <div class="pagination-bar">
            <span>Total: {{ totalUsers() }} usuarios</span>
            <div class="page-controls">
              <button class="btn btn-secondary btn-sm" [disabled]="currentPage() === 1" (click)="changePage(currentPage() - 1)">
                Anterior
              </button>
              <span>Página {{ currentPage() }} de {{ totalPages() }}</span>
              <button class="btn btn-secondary btn-sm" [disabled]="currentPage() >= totalPages()" (click)="changePage(currentPage() + 1)">
                Siguiente
              </button>
            </div>
          </div>
        </div>
      }

      <!-- MODAL DE SANCIÓN / BANEO -->
      @if (banModalUser()) {
        <div class="modal-backdrop">
          <div class="modal-container">
            <div class="modal-header">
              <h3>🚫 Suspender Usuario &#64;{{ banModalUser()?.username }}</h3>
              <button class="btn-close" (click)="banModalUser.set(null)">✕</button>
            </div>
            <div class="modal-body">
              <p class="modal-desc">
                Esta acción bloqueará de inmediato el acceso al chat global y servicios comunitarios para este usuario.
              </p>

              <div class="form-group mt-3">
                <label class="form-label">Motivo de la Sanción *</label>
                <input
                  type="text"
                  class="form-control"
                  placeholder="Ej. Uso de vocabulario ofensivo en el chat global"
                  [(ngModel)]="banReason"
                  required
                />
              </div>

              <div class="form-group">
                <label class="form-label">Duración de la Suspensión</label>
                <select class="form-control" [(ngModel)]="banDurationHours">
                  <option [ngValue]="24">24 Horas (1 Día)</option>
                  <option [ngValue]="72">72 Horas (3 Días)</option>
                  <option [ngValue]="168">7 Días (1 Semana)</option>
                  <option [ngValue]="720">30 Días (1 Mes)</option>
                  <option [ngValue]="0">Permanente (Sin límite)</option>
                </select>
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" (click)="banModalUser.set(null)">Cancelar</button>
              <button class="btn btn-danger" (click)="submitBan()" [disabled]="!banReason.trim()">
                Aplicar Sanción
              </button>
            </div>
          </div>
        </div>
      }

      <!-- MODAL DE CAMBIO DE ROL -->
      @if (roleModalUser()) {
        <div class="modal-backdrop">
          <div class="modal-container">
            <div class="modal-header">
              <h3>👑 Cambiar Rol de &#64;{{ roleModalUser()?.username }}</h3>
              <button class="btn-close" (click)="roleModalUser.set(null)">✕</button>
            </div>
            <div class="modal-body">
              <div class="form-group">
                <label class="form-label">Seleccionar Nivel de Acceso</label>
                <select class="form-control" [(ngModel)]="newRole">
                  <option value="USER">USER (Usuario estándar de la app)</option>
                  <option value="MODERATOR">MODERATOR (Moderador de chat y avisos)</option>
                  <option value="ADMIN">ADMIN (Acceso total al panel de control)</option>
                </select>
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" (click)="roleModalUser.set(null)">Cancelar</button>
              <button class="btn btn-primary" (click)="submitRole()">Guardar Rol</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .users-page {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .page-header h1 {
      font-size: 24px;
      font-weight: 800;
    }

    .subtitle {
      font-size: 14px;
      color: var(--text-muted);
    }

    .filters-card {
      padding: 16px 20px;
    }

    .filters-row {
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;
    }

    .search-box {
      flex: 1;
      min-width: 280px;
      position: relative;
    }

    .search-icon {
      position: absolute;
      left: 12px;
      top: 50%;
      transform: translateY(-50%);
      font-size: 14px;
      opacity: 0.6;
    }

    .search-box .form-control {
      padding-left: 36px;
    }

    .role-select, .status-select {
      width: 170px;
    }

    .user-cell {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: linear-gradient(135deg, #6366f1, #4f46e5);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      flex-shrink: 0;
    }

    .online-border {
      border: 2px solid #10b981;
    }

    .user-meta {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .user-name {
      font-size: 14px;
      color: var(--text-main);
    }

    .user-handle {
      font-size: 12px;
      color: var(--text-dim);
    }

    .uid-tag {
      background: none;
      border: none;
      font-size: 11px;
      color: var(--text-muted);
      font-family: monospace;
      padding: 0;
      text-align: left;
    }

    .uid-tag:hover {
      color: var(--primary);
    }

    .email-meta {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 13px;
    }

    .providers-row {
      display: flex;
      gap: 4px;
    }

    .provider-pill {
      font-size: 10px;
      font-weight: 700;
      padding: 1px 6px;
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.08);
      color: var(--text-muted);
    }

    .device-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .online-indicator {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      font-weight: 600;
    }

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: var(--text-dim);
    }

    .dot.online {
      background-color: #10b981;
      box-shadow: 0 0 8px #10b981;
    }

    .device-name {
      font-size: 11px;
      color: var(--text-muted);
    }

    .listening-tag {
      font-size: 11px;
      color: #818cf8;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 180px;
    }

    .activity-meta {
      display: flex;
      flex-direction: column;
      gap: 2px;
      font-size: 11px;
      color: var(--text-dim);
    }

    .actions-group {
      display: flex;
      gap: 6px;
    }

    .btn-delete:hover {
      background-color: var(--danger);
      color: #fff;
    }

    .pagination-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 16px;
      font-size: 13px;
      color: var(--text-muted);
    }

    .page-controls {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .loading-card, .empty-card {
      padding: 60px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
    }

    .btn-close {
      background: none;
      border: none;
      color: var(--text-muted);
      font-size: 18px;
    }

    .modal-desc {
      font-size: 13px;
      color: var(--text-muted);
      line-height: 1.4;
    }
  `],
})
export class UsersListComponent implements OnInit {
  private readonly usersService = inject(UsersService);

  readonly users = signal<UserListItem[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly currentPage = signal<number>(1);
  readonly totalPages = signal<number>(1);
  readonly totalUsers = signal<number>(0);

  searchQuery = '';
  selectedRole = '';
  selectedOnline = '';

  // Estados de Modales
  readonly banModalUser = signal<UserListItem | null>(null);
  banReason = '';
  banDurationHours = 24;

  readonly roleModalUser = signal<UserListItem | null>(null);
  newRole: 'USER' | 'MODERATOR' | 'ADMIN' = 'USER';

  ngOnInit() {
    this.loadUsers();
  }

  loadUsers() {
    this.isLoading.set(true);
    const onlineBool =
      this.selectedOnline === 'true' ? true : this.selectedOnline === 'false' ? false : undefined;

    this.usersService
      .getUsers({
        page: this.currentPage(),
        limit: 15,
        search: this.searchQuery || undefined,
        role: this.selectedRole || undefined,
        isOnline: onlineBool,
      })
      .subscribe({
        next: (res) => {
          this.users.set(res.data);
          this.totalUsers.set(res.meta.total);
          this.totalPages.set(res.meta.totalPages);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
        },
      });
  }

  applyFilters() {
    this.currentPage.set(1);
    this.loadUsers();
  }

  changePage(page: number) {
    this.currentPage.set(page);
    this.loadUsers();
  }

  copyUid(uid: string) {
    if (typeof navigator !== 'undefined') {
      navigator.clipboard.writeText(uid);
      alert(`UID copiado al portapapeles: ${uid}`);
    }
  }

  getRoleBadgeClass(role: string): string {
    switch (role) {
      case 'ADMIN': return 'badge-danger';
      case 'MODERATOR': return 'badge-warning';
      default: return 'badge-secondary';
    }
  }

  openBanModal(user: UserListItem) {
    this.banModalUser.set(user);
    this.banReason = 'Infracción de normas comunitarias de Audiocinemateca';
    this.banDurationHours = 24;
  }

  submitBan() {
    const user = this.banModalUser();
    if (!user) return;

    this.usersService
      .banUser({
        targetUserId: user.id,
        reason: this.banReason,
        durationHours: this.banDurationHours > 0 ? this.banDurationHours : undefined,
      })
      .subscribe({
        next: () => {
          this.banModalUser.set(null);
          alert(`Usuario @${user.username} suspendido.`);
          this.loadUsers();
        },
        error: (err) => {
          alert(`Error al suspender: ${err?.message || 'Error desconocido'}`);
        },
      });
  }

  unbanUser(user: UserListItem) {
    if (confirm(`¿Levantar la suspensión y desbanear a @${user.username}?`)) {
      this.usersService.unbanUser(user.id).subscribe({
        next: () => {
          alert(`Suspensión levantada para @${user.username}.`);
          this.loadUsers();
        },
        error: (err) => {
          alert(`Error al desbanear: ${err?.message || 'Error desconocido'}`);
        },
      });
    }
  }

  openRoleModal(user: UserListItem) {
    this.roleModalUser.set(user);
    this.newRole = user.role;
  }

  submitRole() {
    const user = this.roleModalUser();
    if (!user) return;

    this.usersService.updateRole(user.id, this.newRole).subscribe({
      next: () => {
        this.roleModalUser.set(null);
        alert(`Rol de @${user.username} actualizado a ${this.newRole}.`);
        this.loadUsers();
      },
      error: (err) => {
        alert(`Error al actualizar rol: ${err?.message || 'Error desconocido'}`);
      },
    });
  }

  confirmDelete(user: UserListItem) {
    if (confirm(`⚠️ ¿Eliminar definitivamente la cuenta de @${user.username}? Esta acción es irreversible.`)) {
      this.usersService.deleteUser(user.id).subscribe({
        next: () => {
          alert(`Cuenta de @${user.username} eliminada.`);
          this.loadUsers();
        },
        error: (err) => {
          alert(`Error al eliminar: ${err?.message || 'Error desconocido'}`);
        },
      });
    }
  }
}
