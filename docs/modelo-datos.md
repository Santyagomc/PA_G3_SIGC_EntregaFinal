# Modelo de datos

Este modelo se ajustó en la Entrega 2 a partir del dump real de la base
de datos del backend actual (`sigcapi.sql`, incluido en esta entrega).
Esquema `sigcapi` en MariaDB 10.4.

## Tablas existentes en el backend actual

- **users**: `id, full_name, email, document, password, rol
  (ADMIN/CONSULTOR), status (ACTIVO/BLOQUEADO/INACTIVO),
  failed_attempts, lock_until, created_at, updated_at`. No tiene
  columnas `city` ni `phone` (a diferencia de lo propuesto en la
  Entrega 1).
- **requests**: `id, title, description, full_name, email, document,
  city, phone, type (PETICIÓN/QUEJA/RECLAMO), status
  (CREADO/PENDIENTE/CERRADO), created_at`. No tiene columna
  `user_id`; la relación con el ciudadano se hace por coincidencia
  del campo `document` (índice `idx_user_document`), no por llave
  foránea.
- **documents**: `id, request_id (FK -> requests.id), file_name,
  file_path, created_at`.
- **sesiones**: `id, full_name, created_at, closure_at`. **No tiene
  columna `request_id`**: hoy una sesión de chatbot no queda
  vinculada a una solicitud PQR, a diferencia de lo descrito en RF-12
  de la Entrega 1.
- **chats**: `id, sesion_id (FK -> sesiones.id), type
  (USUARIO/ASESORIA), mensaje, created_at`. La tabla existe, pero el
  Postman collection actual no expone endpoints para consultarla o
  escribirla directamente (se administra, previsiblemente, dentro del
  flujo interno de `/sessions`).

## Tabla nueva propuesta en esta entrega

- **request_history**: `id, request_id (FK -> requests.id),
  previous_status, new_status, actor, event_timestamp`. Aún no existe
  en `sigcapi.sql`; se propone como destino del consumidor Kafka
  (`@KafkaListener`) descrito en `docs/kafka.md`.

Ver diagrama: `modelo-datos.png`. Las relaciones marcadas en rojo en
el diagrama son brechas detectadas frente al alcance funcional
definido en la Entrega 1 (ver `docs/decisiones-pendientes.md`).

## Actualización — Entrega 2, segundo periodo (Kafka)

Las tablas siguientes las crea JPA (`ddl-auto=update`) y forman parte del flujo orientado a eventos.

```mermaid
erDiagram
    requests ||--o{ request_history : "auditada por (consumidor sigc-audit)"
    users ||--o{ request_history : "actor"
    requests ||..o{ outbox_events : "aggregate_id (sin FK)"
    requests ||..o{ notifications : "request_id (sin FK)"
    outbox_events ||..o{ processed_events : "event_id"
    outbox_events {
        bigint id PK
        varchar event_id UK "UUID"
        varchar event_type
        bigint aggregate_id "requestId = clave Kafka"
        varchar topic
        text payload "JSON contrato v1"
        varchar status "PENDING/PUBLISHED/FAILED"
        int attempts
        varchar last_error
        int kafka_partition
        bigint kafka_offset
        datetime created_at
        datetime published_at
    }
    processed_events {
        bigint id PK
        varchar event_id "UK(event_id, consumer)"
        varchar consumer "audit-history / citizen-notifications"
        varchar event_type
        bigint request_id
        varchar outcome "PROCESADO/OMITIDO"
        datetime processed_at
    }
    request_history {
        bigint id PK
        bigint request_id FK
        varchar status_old "NULL en radicación"
        varchar status_new
        bigint user_id FK
        varchar event_id UK
        varchar observations
        datetime changed_at "momento del hecho"
        datetime recorded_at "momento del consumo"
    }
    notifications {
        bigint id PK
        varchar event_id UK
        bigint request_id
        varchar channel
        varchar recipient
        varchar subject
        text body
        varchar status "SENT/SIMULATED"
        datetime created_at
    }
    failed_events {
        bigint id PK
        varchar original_topic
        int original_partition
        bigint original_offset
        varchar consumer_group
        varchar event_key
        text payload
        varchar exception_class
        varchar error_message
        datetime received_at
    }
```

| Tabla | Quién escribe | Propósito |
|---|---|---|
| `outbox_events` | `RequestServiceImpl` (vía `OutboxRequestEventPublisher`) en la misma transacción; `OutboxRelay` actualiza el estado | Publicación confiable (Transactional Outbox) |
| `request_history` | **Solo** el consumidor `sigc-audit` | Auditoría de la PQR (ya no se escribe dentro de la petición HTTP) |
| `notifications` | Consumidor `sigc-notifications` | Evidencia de cada notificación al ciudadano |
| `processed_events` | Ambos consumidores de negocio | Idempotencia (`UNIQUE(event_id, consumer)`) |
| `failed_events` | Consumidor `sigc-dlt` | Mensajes del Dead Letter Topic para revisión |

Cambios en `request_history` frente a la entrega anterior: `status_old` admite `NULL` (radicación),
se agregan `event_id` (único), `observations` y `recorded_at`, y `changed_at` toma el `timestamp`
del evento. Si se conserva un volumen de MariaDB de la entrega anterior, ejecute
`docker compose down -v` una vez para que JPA cree el esquema nuevo (Hibernate `update` no relaja
restricciones `NOT NULL` existentes).
