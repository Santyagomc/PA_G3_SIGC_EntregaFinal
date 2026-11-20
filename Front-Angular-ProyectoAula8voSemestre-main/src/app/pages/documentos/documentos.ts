import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { DocumentType } from '../../core/models/pqrs.models';
import { AuthService } from '../../core/services/auth.service';
import { PqrsDataService } from '../../core/services/pqrs-data.service';

const TYPE_LABELS: Record<DocumentType, string> = {
  CONTRATO: 'Contrato',
  EVIDENCIA: 'Evidencia',
  FACTURA: 'Factura',
  IDENTIFICACION: 'Identificación',
  OTRO: 'Otro',
  RESPUESTA_ENTIDAD: 'Respuesta entidad',
  SOPORTE: 'Soporte',
};

@Component({
  selector: 'app-documentos',
  imports: [FormsModule],
  templateUrl: './documentos.html',
  styleUrl: './documentos.css',
})
export class DocumentosPage {
  readonly auth = inject(AuthService);
  private readonly data = inject(PqrsDataService);

  readonly search = signal('');
  readonly typeFilter = signal<string>('all');
  readonly uploadOpen = signal(false);
  readonly selectedRequestId = signal<number | null>(null);
  readonly selectedFiles = signal<File[]>([]);
  readonly uploadError = signal('');
  readonly uploading = signal(false);

  readonly labels = TYPE_LABELS;

  readonly availableRequests = computed(() => {
    if (this.auth.isStaff()) return this.data.requests();
    return this.data.requests().filter((r) => r.document === this.auth.document());
  });

  readonly userDocs = computed(() => {
    if (this.auth.isStaff()) return this.data.documents();
    const myReqIds = new Set(this.data.requests().filter((r) => r.document === this.auth.document()).map((r) => r.id));
    return this.data.documents().filter((d) => myReqIds.has(d.request_id));
  });

  readonly filtered = computed(() => {
    const q = this.search().toLowerCase();
    return this.userDocs().filter((d) => {
      const ok = !q || d.file_name.toLowerCase().includes(q);
      const ty = this.typeFilter() === 'all' || d.type === this.typeFilter();
      return ok && ty;
    });
  });

  readonly stats = computed(() => {
    const list = this.userDocs();
    const byType: Record<string, number> = {};
    for (const d of list) {
      byType[d.type] = (byType[d.type] ?? 0) + 1;
    }
    const entries = Object.entries(byType).slice(0, 4);
    return { total: list.length, byType: entries };
  });

  requestTitle(id: number): string {
    const r = this.data.requests().find((x) => x.id === id);
    return r ? `#${id} — ${r.title}` : `#${id}`;
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('es-CO', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  badgeClass(t: DocumentType): string {
    return 'sigc-badge sigc-badge--muted';
  }

  typeStatLabel(key: string): string {
    return this.labels[key as DocumentType] ?? key;
  }

  openUpload(): void {
    const firstRequest = this.availableRequests()[0]?.id ?? null;
    this.uploadError.set('');
    this.selectedFiles.set([]);
    this.selectedRequestId.set(firstRequest);

    if (!firstRequest) {
      this.uploadError.set('Primero debes registrar una solicitud en esta sesión para poder asociar documentos.');
    }

    this.uploadOpen.set(true);
  }

  closeUpload(): void {
    this.uploadOpen.set(false);
    this.uploading.set(false);
    this.uploadError.set('');
    this.selectedFiles.set([]);
  }

  onRequestChange(value: string): void {
    this.selectedRequestId.set(value ? Number(value) : null);
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFiles.set(Array.from(input.files ?? []));
  }

  submitUpload(): void {
    const idRequest = this.selectedRequestId();
    const files = this.selectedFiles();

    if (!idRequest) {
      this.uploadError.set('Seleccione una solicitud.');
      return;
    }

    if (!files.length) {
      this.uploadError.set('Seleccione al menos un archivo.');
      return;
    }

    this.uploading.set(true);
    this.uploadError.set('');
    this.data.uploadDocumentsRemote(idRequest, files).subscribe((res) => {
      this.uploading.set(false);
      if (res.ok) {
        this.closeUpload();
        return;
      }
      this.uploadError.set(res.error);
    });
  }
}
