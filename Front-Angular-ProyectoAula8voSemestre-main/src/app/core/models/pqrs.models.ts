export type RequestStatus = 'CREADO' | 'PENDIENTE' | 'CERRADO';
export type RequestType = 'PETICIÓN' | 'QUEJA' | 'RECLAMO';
export type UserStatus = 'ACTIVO' | 'BLOQUEADO' | 'INACTIVO';
/** Backend usa CONSULTOR; la UI puede mostrar «Ciudadano». */
export type UserRole = 'ADMIN' | 'FUNCIONARIO' | 'CIUDADANO' | 'CONSULTOR';
export type DocumentType =
  | 'CONTRATO'
  | 'EVIDENCIA'
  | 'FACTURA'
  | 'IDENTIFICACION'
  | 'OTRO'
  | 'RESPUESTA_ENTIDAD'
  | 'SOPORTE';

export interface UiUser {
  id: number;
  created_at: string;
  document: string;
  email: string;
  failed_attempts: number;
  full_name: string;
  lock_until: string | null;
  rol: UserRole;
  status: UserStatus;
  updated_at: string;
}

export interface UiRequest {
  id: number;
  city: string;
  created_at: string;
  description: string;
  document: string;
  email: string;
  full_name: string;
  phone: string;
  status: RequestStatus;
  title: string;
  type: RequestType;
}

export interface UiDocument {
  id: number;
  created_at: string;
  file_name: string;
  file_path: string;
  type: DocumentType;
  request_id: number;
}

export interface DashboardStats {
  totalRequests: number;
  pendingRequests: number;
  closedRequests: number;
  activeUsers: number;
  requestsByType: { peticiones: number; quejas: number; reclamos: number };
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

export interface ApiResponse<T> {
  exito: boolean;
  mensaje: string;
  data?: T;
}

export interface LoginSuccessResponse {
  exito: boolean;
  mensaje?: string;
  token: string;
  usuario: string;
  roles: string[];
}
