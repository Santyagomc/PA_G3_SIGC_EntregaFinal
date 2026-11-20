import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { UiUser, UserRole, UserStatus } from '../../core/models/pqrs.models';
import { PqrsDataService } from '../../core/services/pqrs-data.service';

@Component({
  selector: 'app-usuarios',
  imports: [FormsModule],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css',
})
export class UsuariosPage {
  private readonly data = inject(PqrsDataService);

  readonly search = signal('');
  readonly roleFilter = signal<string>('all');
  readonly statusFilter = signal<string>('all');
  readonly selected = signal<UiUser | null>(null);

  readonly filtered = computed(() => {
    const q = this.search().toLowerCase();
    return this.data.users().filter((u) => {
      const ok =
        !q ||
        u.full_name.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q) ||
        u.document.includes(q);
      const r = this.roleFilter() === 'all' || u.rol === this.roleFilter();
      const s = this.statusFilter() === 'all' || u.status === this.statusFilter();
      return ok && r && s;
    });
  });

  readonly stats = computed(() => {
    const u = this.data.users();
    return {
      total: u.length,
      activos: u.filter((x) => x.status === 'ACTIVO').length,
      bloqueados: u.filter((x) => x.status === 'BLOQUEADO').length,
    };
  });

  initials(name: string): string {
    return name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .substring(0, 2)
      .toUpperCase();
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  statusClass(s: UserStatus): string {
    if (s === 'ACTIVO') return 'sigc-badge sigc-badge--ok';
    if (s === 'BLOQUEADO') return 'sigc-badge sigc-badge--type-q';
    return 'sigc-badge sigc-badge--muted';
  }

  roleLabel(r: UserRole): string {
    if (r === 'ADMIN') return 'ADMIN';
    if (r === 'FUNCIONARIO') return 'FUNCIONARIO';
    if (r === 'CONSULTOR') return 'CONSULTOR';
    return 'CIUDADANO';
  }

  openUser(u: UiUser): void {
    this.selected.set(u);
  }

  closeUser(): void {
    this.selected.set(null);
  }
}
