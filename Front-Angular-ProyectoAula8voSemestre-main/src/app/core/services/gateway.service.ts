import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Consulta del dashboard contra el Gateway GraphQL (ajustar aquí si cambia el schema). */
export const RESUMEN_INDICADORES_QUERY = `query ResumenIndicadores {
  resumenIndicadores {
    totalSolicitudes
    solicitudesAbiertas
    solicitudesCerradas
    eventosProcesados
    notificaciones
    mensajesDlt
    generadoEn
    desactualizado
  }
}`;

export interface ResumenIndicadores {
  totalSolicitudes: number;
  solicitudesAbiertas: number;
  solicitudesCerradas: number;
  eventosProcesados?: number | null;
  notificaciones?: number | null;
  mensajesDlt?: number | null;
  generadoEn?: string | null;
  desactualizado?: boolean | null;
}

interface GraphQlResponse<T> {
  data?: T | null;
  errors?: { message: string; extensions?: Record<string, unknown> }[];
}

function newCorrelationId(): string {
  const c = globalThis.crypto as Crypto | undefined;
  return c && typeof c.randomUUID === 'function'
    ? c.randomUUID()
    : `fe-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

@Injectable({ providedIn: 'root' })
export class GatewayService {
  private readonly url = environment.gatewayUrl;

  constructor(private readonly http: HttpClient) {}

  /** Ejecuta una operación GraphQL; el interceptor de auth agrega el Bearer token. */
  query<T>(query: string, variables?: Record<string, unknown>): Observable<T> {
    const headers = new HttpHeaders({ 'X-Correlation-Id': newCorrelationId() });
    return this.http
      .post<GraphQlResponse<T>>(this.url, { query, variables }, { headers })
      .pipe(
        map((res) => {
          if (res.errors?.length || !res.data) {
            throw new Error(res.errors?.[0]?.message ?? 'Respuesta GraphQL vacía');
          }
          return res.data;
        }),
      );
  }

  resumenIndicadores(): Observable<ResumenIndicadores> {
    return this.query<{ resumenIndicadores: ResumenIndicadores }>(RESUMEN_INDICADORES_QUERY).pipe(
      map((d) => d.resumenIndicadores),
    );
  }
}
