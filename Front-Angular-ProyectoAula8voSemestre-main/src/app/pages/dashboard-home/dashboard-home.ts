import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { PqrsDataService } from '../../core/services/pqrs-data.service';
import { GatewayService, ResumenIndicadores } from '../../core/services/gateway.service';

@Component({
  selector: 'app-dashboard-home',
  templateUrl: './dashboard-home.html',
  styleUrl: './dashboard-home.css',
})
export class DashboardHome {
  private readonly auth = inject(AuthService);
  private readonly data = inject(PqrsDataService);
  private readonly gateway = inject(GatewayService);

  /** Indicadores obtenidos del Gateway GraphQL (null = no disponibles, se usan los datos locales). */
  readonly gatewayStats = signal<ResumenIndicadores | null>(null);
  readonly gatewayError = signal(false);

  constructor() {
    if (this.auth.isAdmin()) {
      this.gateway.resumenIndicadores().subscribe({
        next: (r) => {
          this.gatewayStats.set(r);
          this.gatewayError.set(false);
        },
        error: () => this.gatewayError.set(true),
      });
    }
  }

  readonly stats = computed(() => {
    const requests = this.data.requests();
    const users = this.data.users();
    const gw = this.gatewayStats();
    return {
      totalRequests: gw ? gw.totalSolicitudes : requests.length,
      pendingRequests: gw ? gw.solicitudesAbiertas : requests.filter((item) => item.status === 'PENDIENTE').length,
      closedRequests: gw ? gw.solicitudesCerradas : requests.filter((item) => item.status === 'CERRADO').length,
      activeUsers: this.auth.isAdmin()
        ? users.filter((item) => item.status === 'ACTIVO').length
        : this.auth.isAuthenticated()
          ? 1
          : 0,
      requestsByType: {
        peticiones: requests.filter((item) => item.type === 'PETICIÓN').length,
        quejas: requests.filter((item) => item.type === 'QUEJA').length,
        reclamos: requests.filter((item) => item.type === 'RECLAMO').length,
      },
    };
  });

  readonly userFirstName = computed(() => {
    const doc = this.auth.document();
    const u = this.data.users().find((x) => x.document === doc);
    if (u?.full_name) return u.full_name.split(' ')[0];
    return doc ? `Doc. ${doc}` : 'Usuario';
  });

  readonly recent = computed(() => this.data.requests().slice(0, 5));

  readonly barData = computed(() => {
    const requests = this.data.requests();
    const grouped = new Map<string, number>();

    for (const request of requests) {
      const date = new Date(request.created_at);
      if (Number.isNaN(date.getTime())) continue;
      const label = date.toLocaleDateString('es-CO', { month: 'short' });
      grouped.set(label, (grouped.get(label) ?? 0) + 1);
    }

    const entries = Array.from(grouped.entries()).slice(-6);
    if (entries.length) {
      return entries.map(([name, v]) => ({ name, v }));
    }

    return [{ name: 'Sin datos', v: 0 }];
  });

  pie = computed(() => {
    const s = this.stats().requestsByType;
    const t = s.peticiones + s.quejas + s.reclamos || 1;
    return [
      { label: 'Peticiones', value: s.peticiones, pct: (s.peticiones / t) * 100, color: 'var(--chart-1)' },
      { label: 'Quejas', value: s.quejas, pct: (s.quejas / t) * 100, color: 'var(--chart-4)' },
      { label: 'Reclamos', value: s.reclamos, pct: (s.reclamos / t) * 100, color: 'var(--chart-3)' },
    ];
  });

  maxBar = computed(() => Math.max(...this.barData().map((b) => b.v), 1));

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  statusClass(s: string): string {
    if (s === 'CREADO') return 'sigc-badge sigc-badge--muted';
    if (s === 'PENDIENTE') return 'sigc-badge sigc-badge--warn';
    return 'sigc-badge sigc-badge--ok';
  }

  typeClass(t: string): string {
    if (t === 'PETICIÓN') return 'sigc-badge sigc-badge--type-p';
    if (t === 'QUEJA') return 'sigc-badge sigc-badge--type-q';
    return 'sigc-badge sigc-badge--type-r';
  }
}
