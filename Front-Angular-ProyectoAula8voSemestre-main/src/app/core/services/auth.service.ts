import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { ApiResponse, LoginSuccessResponse } from '../models/pqrs.models';

const TOKEN = 'sigc_token';
const DOCUMENT = 'sigc_document';
const ROLES = 'sigc_roles';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = environment.apiUrl;

  readonly token = signal<string | null>(localStorage.getItem(TOKEN));
  readonly document = signal<string | null>(localStorage.getItem(DOCUMENT));
  readonly roles = signal<string[]>(this.readRoles());

  readonly isAuthenticated = computed(() => !!this.token());
  readonly primaryRole = computed(() => this.roles()[0] ?? null);

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  private readRoles(): string[] {
    try {
      const raw = localStorage.getItem(ROLES);
      return raw ? (JSON.parse(raw) as string[]) : [];
    } catch {
      return [];
    }
  }

  /** Admin del sistema (ruta usuarios). */
  isAdmin(): boolean {
    return this.roles().some((r) => r === 'ADMIN');
  }

  /** Ve todas las solicitudes / documentos (admin o funcionario en demo). */
  isStaff(): boolean {
    return this.roles().some((r) => r === 'ADMIN');
  }

  hasRole(roles: string[]): boolean {
    const mine = this.roles();
    if (!mine.length) return false;
    return roles.some((r) => {
      if (r === 'FUNCIONARIO') return mine.some((m) => m === 'FUNCIONARIO' || m === 'CONSULTOR');
      if (r === 'CIUDADANO') return mine.some((m) => m === 'CIUDADANO' || m === 'CONSULTOR');
      return mine.includes(r);
    });
  }

  login(username: string, password: string): Observable<{ ok: true } | { ok: false; error: string }> {
    return this.http.post<LoginSuccessResponse | ApiResponse<unknown>>(`${this.api}/auth/login`, {
      username: username.trim(),
      password,
    }).pipe(
      map((res) => {
        const r = res as LoginSuccessResponse;
        if (r.token && r.usuario) {
          localStorage.setItem(TOKEN, r.token);
          localStorage.setItem(DOCUMENT, r.usuario);
          const roles = r.roles ?? [];
          localStorage.setItem(ROLES, JSON.stringify(roles));
          this.token.set(r.token);
          this.document.set(r.usuario);
          this.roles.set(roles);
          return { ok: true as const };
        }
        const err = res as ApiResponse<unknown>;
        return { ok: false as const, error: err.mensaje || 'No se pudo iniciar sesión' };
      }),
      catchError((e) => {
        const body = e?.error;
        const msg =
          typeof body?.mensaje === 'string'
            ? body.mensaje
            : typeof body?.message === 'string'
              ? body.message
              : 'Error de conexión con el servidor';
        return of({ ok: false as const, error: msg });
      }),
    );
  }

  register(payload: {
    name: string;
    email: string;
    document: string;
    password: string;
  }): Observable<{ ok: true } | { ok: false; error: string }> {
    const body = {
      name: payload.name,
      email: payload.email,
      document: payload.document,
      password: payload.password,
      admin: false,
      status: 'ACTIVO',
    };
    return this.http.post<ApiResponse<unknown>>(`${this.api}/auth/register`, body).pipe(
      map((res) => {
        if (res.exito) return { ok: true as const };
        return { ok: false as const, error: res.mensaje || 'Error al registrarse' };
      }),
      catchError((e) => {
        const b = e?.error;
        const msg = typeof b?.mensaje === 'string' ? b.mensaje : 'Error de registro';
        return of({ ok: false as const, error: msg });
      }),
    );
  }

  registerAndLogin(
    payload: { name: string; email: string; document: string; password: string },
  ): Observable<{ ok: true } | { ok: false; error: string }> {
    return this.register(payload).pipe(
      switchMap((r) => {
        if (!r.ok) return of(r);
        return this.login(payload.document, payload.password);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN);
    localStorage.removeItem(DOCUMENT);
    localStorage.removeItem(ROLES);
    this.token.set(null);
    this.document.set(null);
    this.roles.set([]);
    void this.router.navigate(['/login']);
  }
}
