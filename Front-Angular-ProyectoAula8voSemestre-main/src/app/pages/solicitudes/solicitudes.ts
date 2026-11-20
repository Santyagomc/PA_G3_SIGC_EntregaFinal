import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { RequestStatus, RequestType, UiRequest } from '../../core/models/pqrs.models';
import { AuthService } from '../../core/services/auth.service';
import { PqrsDataService } from '../../core/services/pqrs-data.service';

@Component({
  selector: 'app-solicitudes',
  imports: [FormsModule],
  templateUrl: './solicitudes.html',
  styleUrl: './solicitudes.css',
})
export class SolicitudesPage {
  readonly auth = inject(AuthService);
  private readonly data = inject(PqrsDataService);

  readonly search = signal('');
  readonly statusFilter = signal<string>('all');
  readonly typeFilter = signal<string>('all');
  readonly dialogOpen = signal(false);
  readonly detailOpen = signal(false);
  readonly selected = signal<UiRequest | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');

  readonly newReq = signal({
    title: '',
    type: 'PETICIÓN' as RequestType,
    city: '',
    description: '',
    phone: '',
    full_name: '',
    email: '',
  });

  patchNewReq(field: 'title' | 'type' | 'city' | 'description' | 'phone' | 'full_name' | 'email', value: string | RequestType): void {
    this.newReq.update((n) => ({ ...n, [field]: value } as typeof n));
  }

  readonly baseList = computed(() => {
    const doc = this.auth.document();
    if (this.auth.isStaff()) return this.data.requests();
    return this.data.requests().filter((r) => r.document === doc);
  });

  readonly filtered = computed(() => {
    const q = this.search().toLowerCase();
    return this.baseList().filter((r) => {
      const okSearch =
        !q ||
        r.title.toLowerCase().includes(q) ||
        r.full_name.toLowerCase().includes(q) ||
        r.city.toLowerCase().includes(q);
      const okSt = this.statusFilter() === 'all' || r.status === this.statusFilter();
      const okTy = this.typeFilter() === 'all' || r.type === this.typeFilter();
      return okSearch && okSt && okTy;
    });
  });

  openCreate(): void {
    this.formError.set('');
    const u = this.data.users().find((x) => x.document === this.auth.document());
    this.newReq.set({
      title: '',
      type: 'PETICIÓN',
      city: '',
      description: '',
      phone: '',
      full_name: u?.full_name ?? '',
      email: u?.email ?? '',
    });
    this.dialogOpen.set(true);
  }

  closeCreate(): void {
    this.dialogOpen.set(false);
  }

  submitCreate(): void {
    const v = this.newReq();
    const doc = this.auth.document();
    if (!doc) {
      this.formError.set('Sesión inválida.');
      return;
    }
    const fn = v.full_name.trim();
    const em = v.email.trim();
    if (!v.title.trim() || !v.city || !v.description.trim() || !v.phone.trim() || !fn || !em) {
      this.formError.set('Complete todos los campos obligatorios (incl. nombre y correo).');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    this.data
      .saveRequestRemote({
        title: v.title.trim(),
        description: v.description.trim(),
        fullName: fn,
        email: em,
        document: doc,
        city: v.city,
        phone: v.phone.trim(),
        type: v.type,
      })
      .subscribe((res) => {
        this.saving.set(false);
        if (res.ok) {
          this.dialogOpen.set(false);
          this.newReq.set({
            title: '',
            type: 'PETICIÓN',
            city: '',
            description: '',
            phone: '',
            full_name: '',
            email: '',
          });
        } else {
          this.formError.set(res.error);
        }
      });
  }

  openDetail(r: UiRequest): void {
    this.selected.set(r);
    this.detailOpen.set(true);
  }

  closeDetail(): void {
    this.detailOpen.set(false);
    this.selected.set(null);
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  statusClass(s: RequestStatus): string {
    if (s === 'CREADO') return 'sigc-badge sigc-badge--muted';
    if (s === 'PENDIENTE') return 'sigc-badge sigc-badge--warn';
    return 'sigc-badge sigc-badge--ok';
  }

  typeClass(t: RequestType): string {
    if (t === 'PETICIÓN') return 'sigc-badge sigc-badge--type-p';
    if (t === 'QUEJA') return 'sigc-badge sigc-badge--type-q';
    return 'sigc-badge sigc-badge--type-r';
  }
}
