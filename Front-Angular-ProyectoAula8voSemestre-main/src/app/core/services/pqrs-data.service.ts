import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import type { ApiResponse, RequestStatus, RequestType, UiDocument, UiRequest, UiUser } from '../models/pqrs.models';

/** Entidad solicitud tal como la serializa el backend (Jackson). */
interface RequestEntity {
  id: number;
  title: string;
  description: string;
  fullName: string;
  email: string;
  document: string;
  city: string;
  phone: string;
  type: RequestType;
  status: RequestStatus;
  createdAt: string;
}

interface UserEntity {
  id: number;
  fullName: string;
  email: string;
  document: string;
  rol: string;
  status: string;
  failedAttempts: number;
  createdAt: string;
  updatedAt: string;
  lockUntil: string | null;
}

interface DocumentEntity {
  id: number;
  fileName: string;
  filePath: string;
  createdAt?: string;
  request?: { id: number } | null;
}

function mapRequest(e: RequestEntity): UiRequest {
  return {
    id: e.id,
    title: e.title,
    description: e.description,
    full_name: e.fullName,
    email: e.email,
    document: e.document,
    city: e.city,
    phone: e.phone,
    type: e.type,
    status: e.status,
    created_at: e.createdAt,
  };
}

function mapUser(e: UserEntity): UiUser {
  return {
    id: e.id,
    full_name: e.fullName,
    email: e.email,
    document: e.document,
    rol: e.rol as UiUser['rol'],
    status: e.status as UiUser['status'],
    failed_attempts: e.failedAttempts ?? 0,
    lock_until: e.lockUntil,
    created_at: e.createdAt,
    updated_at: e.updatedAt,
  };
}

function mapDocument(e: DocumentEntity): UiDocument {
  const rid = e.request && typeof e.request === 'object' ? e.request.id : 0;
  return {
    id: e.id,
    file_name: e.fileName,
    file_path: e.filePath,
    type: 'SOPORTE',
    request_id: rid,
    created_at: e.createdAt ?? new Date().toISOString(),
  };
}

@Injectable({ providedIn: 'root' })
export class PqrsDataService {
  private readonly api = environment.apiUrl;

  readonly requests = signal<UiRequest[]>([]);
  readonly users = signal<UiUser[]>([]);
  readonly documents = signal<UiDocument[]>([]);

  constructor(private readonly http: HttpClient) {}

  syncForSession(document: string | null, isAdmin: boolean): void {
    if (!document) {
      this.requests.set([]);
      this.users.set([]);
      this.documents.set([]);
      return;
    }

    if (isAdmin) {
      this.syncAdminData();
      return;
    }

    // El backend actual no expone endpoints para perfil, solicitudes o documentos del ciudadano autenticado.
    this.users.set([]);
    this.requests.set([]);
    this.documents.set([]);
  }

  private syncAdminData(): void {
    forkJoin({
      req: this.http.get<ApiResponse<RequestEntity[] | PageData<RequestEntity>>>(`${this.api}/requests/all?size=100`).pipe(
        map((r) => asList(r.data).map(mapRequest)),
        catchError(() => of(null)),
      ),
      usr: this.http.get<ApiResponse<UserEntity[] | PageData<UserEntity>>>(`${this.api}/admin/users/all?size=100`).pipe(
        map((r) => asList(r.data).map(mapUser)),
        catchError(() => of(null)),
      ),
      doc: this.http.get<ApiResponse<DocumentEntity[] | PageData<DocumentEntity>>>(`${this.api}/documents/all?size=100`).pipe(
        map((r) => asList(r.data).map(mapDocument)),
        catchError(() => of(null)),
      ),
    }).subscribe(({ req, usr, doc }) => {
      if (req) this.requests.set(req);
      if (usr) this.users.set(usr);
      if (doc) this.documents.set(doc);
    });
  }

  addLocalRequest(r: UiRequest): void {
    this.requests.update((list) => [r, ...list]);
  }

  addLocalDocuments(documents: UiDocument[]): void {
    this.documents.update((list) => [...documents, ...list]);
  }

  saveRequestRemote(body: {
    title: string;
    description: string;
    fullName: string;
    email: string;
    document: string;
    city: string;
    phone: string;
    type: RequestType;
  }) {
    return this.http.post<ApiResponse<RequestEntity>>(`${this.api}/requests/save`, body).pipe(
      map((res) => {
        if (res.data) {
          const ui = mapRequest(res.data);
          this.addLocalRequest(ui);
          return { ok: true as const, data: ui };
        }
        return { ok: false as const, error: res.mensaje };
      }),
      catchError((e) => {
        const msg = e?.error?.mensaje ?? 'No se pudo guardar';
        return of({ ok: false as const, error: msg });
      }),
    );
  }

  uploadDocumentsRemote(idRequest: number, files: File[]) {
    const formData = new FormData();
    formData.append('idRequest', String(idRequest));
    files.forEach((file) => formData.append('files', file));

    return this.http.post<ApiResponse<DocumentEntity[]>>(`${this.api}/documents/saveAll`, formData).pipe(
      map((res) => {
        if (res.data) {
          const uploaded = res.data.map(mapDocument);
          this.addLocalDocuments(uploaded);
          return { ok: true as const, data: uploaded };
        }
        return { ok: false as const, error: res.mensaje || 'No se pudieron cargar los documentos' };
      }),
      catchError((e) => {
        const msg = e?.error?.mensaje ?? 'No se pudieron cargar los documentos';
        return of({ ok: false as const, error: msg });
      }),
    );
  }
}

/** Página que devuelven los listados administrativos de la API (PageResponseDTO). */
interface PageData<T> {
  content: T[];
}

/** Acepta tanto una lista como una página de la API y devuelve siempre una lista. */
function asList<T>(data: T[] | PageData<T> | null | undefined): T[] {
  if (!data) return [];
  return Array.isArray(data) ? data : (data.content ?? []);
}
