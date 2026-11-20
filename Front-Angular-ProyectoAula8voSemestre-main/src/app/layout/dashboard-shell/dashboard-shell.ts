import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { PqrsDataService } from '../../core/services/pqrs-data.service';

@Component({
  selector: 'app-dashboard-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './dashboard-shell.html',
  styleUrl: './dashboard-shell.css',
})
export class DashboardShell implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly data = inject(PqrsDataService);

  readonly collapsed = signal(false);

  readonly showUsuarios = computed(() => this.auth.isAdmin());

  readonly displayName = computed(() => {
    const d = this.auth.document();
    const user = this.data.users().find((entry) => entry.document === d);
    return user?.full_name ?? (d ? `Usuario ${d}` : 'Usuario');
  });

  readonly displayRole = computed(() => {
    const r = this.auth.primaryRole();
    if (r === 'CONSULTOR') return 'Ciudadano';
    if (r === 'ADMIN') return 'Administrador';
    if (r === 'FUNCIONARIO') return 'Funcionario';
    return r ?? '—';
  });

  ngOnInit(): void {
    this.data.syncForSession(this.auth.document(), this.auth.isAdmin());
  }

  toggleSidebar(): void {
    this.collapsed.update((c) => !c);
  }

  logout(): void {
    this.auth.logout();
  }
}
