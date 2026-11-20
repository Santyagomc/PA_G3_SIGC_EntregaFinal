# Datos para actualizar el archivo compartido de seguimiento

| Campo | Valor |
|---|---|
| Título | SIGC: Sistema Inteligente de Gestión de PQR con arquitectura distribuida orientada a eventos (API REST, Kafka y Gateway GraphQL) |
| Grupo / Equipo | Grupo 3 · Equipo 4 |
| Integrantes | Santiago Murcia Cangrejo; Hallys Andrea Escobar Paloma |
| Asignatura / Docente | Programación Avanzada · Diego Fernando Vargas Ramírez |
| Fecha de seguimiento | 19/11/2026 |
| Estado | Entregado (MVP final, versión v3.0.0) |
| Avance | 90 % del alcance aprobado — MVP funcional de extremo a extremo |

**Implementado:** API REST con JWT y roles; Kafka (outbox, auditoría, notificación, idempotencia, DLT); gateway GraphQL (queries, mutations, resolvers) con seguridad, CORS, timeout/retry/circuit breaker y caché Redis; correlation ID de extremo a extremo; Docker Compose con todos los servicios; pruebas unitarias, de integración y de contrato.

**Parcial:** frontend consume el gateway solo en los indicadores del dashboard; correo en modo simulado por defecto; métricas solo con Actuator.

**Pendiente (no registrar como implementado):** SSE/WebSocket (descartado por no requerirse tiempo real); `GET /documents/my`; alta disponibilidad (varios brokers / réplicas de la API); trazas distribuidas con OpenTelemetry.

> El porcentaje de avance es una valoración del equipo sobre el alcance aprobado; ajústenlo si el docente usa otro criterio.
