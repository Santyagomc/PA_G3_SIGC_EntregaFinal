# Evidencias de funcionamiento — SIGC (Entrega 2 P2 · Kafka)

Guion para construir (o reproducir) el PDF corto de evidencias. Use capturas del entorno local; no incluya secretos ni tokens completos. Todo el flujo gira alrededor de **una misma PQR** para poder seguir los identificadores de una captura a otra.

## Preparación

```bash
cp .env.example .env
docker compose up --build -d
bash scripts/demo-kafka.sh        # guarda cada salida en evidencias/salidas/NN_*.txt
```

## Capturas requeridas

| # | Evidencia | Cómo obtenerla | Qué debe verse |
|---|---|---|---|
| 1 | Servicios activos | `docker compose ps` | `db` y `kafka` *healthy*, `api` y `kafka-ui` activos (`kafka-init` termina solo) |
| 2 | Tópicos | `kafka-topics.sh --describe` (salida `01_topicos_describe.txt`) | `audit-events` y `audit-events.DLT`: 3 particiones, retención 7 y 30 días |
| 3 | Login ADMIN | `POST /auth/login` con `ADMIN_DOCUMENT` / `ADMIN_PASSWORD` del `.env` | 200 OK (token recortado) |
| 4 | Productor: radicación | `POST /requests/save` (multipart: parte `request` JSON + `files`) | 201 y evento `REQUEST_CREATED` |
| 5 | Productor: cambio de estado | `PUT /requests/{id}/status?status=CERRADO` | 200 y evento `REQUEST_STATUS_CHANGED` |
| 6 | Outbox | `GET /events/outbox?requestId={id}` | Eventos `PUBLISHED` con partición y offset |
| 7 | Mensajes en el tópico | `kafka-console-consumer.sh` sobre `audit-events` | Clave = `requestId`, headers `x-event-*` y JSON |
| 8 | Consumidor `sigc-audit` | `GET /requests/{id}/history` | Filas de historial con el `eventId` de origen |
| 9 | Consumidor `sigc-notifications` | `GET /events/notifications?requestId={id}` y `GET /requests/findById/{id}` | Notificaciones `SIMULATED` y `mailStatus = true` |
| 10 | Idempotencia | `POST /events/simulate/duplicate/{eventId}` | Logs «Evento duplicado ignorado»; conteos de historial y notificaciones sin cambios |
| 11 | Errores y DLT | `POST /events/simulate/invalid-payload`, `/contract-violation`, `/unknown-request` | Mensajes en `audit-events.DLT` y filas en `GET /events/dead-letters` |
| 12 | Resumen y logs | `GET /events/summary` y `docker compose logs api \| grep -E "OUTBOX\|KAFKA\|AUDITORIA\|NOTIFICACION\|SIMULADOR"` | Outbox sin `FAILED`; líneas con `[eventId=… requestId=…]` |
| 13 | Pruebas automáticas | `cd api.documental && bash mvnw test` (o solo `-Dtest=KafkaEventFlowIntegrationTest`) | `BUILD SUCCESS`, 0 fallos |

Opcional: un `401` (ruta protegida sin token) y un `403` (usuario CONSULTOR llamando a `/events/summary`), ya que `/events/**` es solo ADMIN.

## Nombres de consumidor

El nombre de cada consumidor es **el mismo** en todas partes: grupo Kafka, columna `consumer` de `processed_events` y prefijo de los logs (`[KAFKA-CONSUMER:sigc-audit]`).

| Consumidor | Grupo Kafka / `processed_events.consumer` |
|---|---|
| Auditoría | `sigc-audit` |
| Notificaciones | `sigc-notifications` |
| Dead Letter | `sigc-dlt` |

## Contenido del PDF

Portada breve, una captura por cada fila de la tabla anterior, la correspondencia con los criterios de evaluación y la referencia a `contracts/asyncapi.yaml`, `contracts/openapi.yaml` y `docs/kafka.md`.
