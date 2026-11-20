# Pruebas y evidencias — SIGC v3.0.0

Fecha de ejecución: 19/11/2026. Entorno: Windows 11, Docker 29.4.3 / Compose 5.1.3, JDK 21.0.9.

**Tipo de resultado:** *Medido* = ejecutado contra el sistema real (Docker Compose) o la suite automatizada; *Simulado* = el escenario se provoca a propósito (API detenida, mensajes inválidos) o el efecto externo no es real (correo en modo `SIMULATED`); *Futuro* = no implementado.

## 1. Pruebas automatizadas (medido)

| Módulo | Comando | Total | Fallos | Errores | Evidencia |
|---|---|---:|---:|---:|---|
| `api.documental` | `./mvnw test` | 136 | 0 | 0 | [evidencias/pruebas/resumen_surefire.txt](../evidencias/pruebas/resumen_surefire.txt) |
| `gateway` | `./mvnw test` | 24 | 0 | 0 | ídem |

| Tipo | Clases (módulo) | Qué cubre |
|---|---|---|
| Unitarias | `RequestServiceImplTest`, `UserServiceImplTest`, `DocumentServiceImplTest`, `ExpertEngineServiceImplTest`, `RuleServiceImplTest`, `OperatorTest`, `DtoValidationTest`, `GlobalExceptionHandlerTest`, `JwtServiceTest`, `CorrelationIdFilterTest`, `EventCodecTest`, `OutboxRelayTest`, `OutboxRequestEventPublisherTest`, `AuditEventProcessorTest`, `NotificationProcessorTest`, `EventConsumersTest` (API); `ResolversUnitTest` (gateway) | Reglas de negocio, validaciones, JWT con claim `roles`, correlation ID, serialización de eventos, idempotencia, composición de resolvers |
| Integración | `ApiIntegrationTests`, `SigcapiApplicationTests` (H2), `KafkaEventFlowIntegrationTest` (Kafka embebido) (API); `GatewaySecurityGraphQlTest` (HTTP real, API simulada) (gateway) | REST + seguridad 401/403, flujo API → outbox → Kafka → consumidores → BD, DLT; GraphQL + seguridad + CORS + errores |
| Resiliencia | `ApiClientResilienceTest` (gateway) | Retry ×3 en GET 5xx, sin retry en mutations, 4xx no abren el circuito, circuito abierto falla en < 200 ms |
| Contrato | `SchemaContractTest` (schema del gateway = `contracts/schema.graphql`); `ApiClientResilienceTest` (forma de `PageResponseDTO` de la API); `EventCodecTest` (evento vs. `request-event.schema.json`) | Productor y consumidor respetan el mismo contrato |

## 2. Matriz de pruebas de extremo a extremo (`bash scripts/demo-final.sh`)

| # | Caso | Resultado esperado | Resultado obtenido | Estado | Tipo | Evidencia |
|---|---|---|---|---|---|---|
| E01 | Levantar la arquitectura con `docker compose up --build -d` | 8 servicios; db/kafka/redis/api *healthy*; kafka-init termina | 7 servicios en ejecución + kafka-init `exited (0)` | ✅ Aprobado | Medido | `evidencias/final/00_servicios.txt` |
| E02 | Salud de API y gateway | `UP`; gateway muestra circuit breaker `CLOSED` y Redis `UP` | `UP`, `circuitBreaker: CLOSED`, `redis: UP` | ✅ | Medido | `01_salud.txt` |
| E03 | Query protegida sin token | Error `UNAUTHORIZED` | `codigo: UNAUTHORIZED` | ✅ | Medido | `02_sin_token.txt` |
| E04 | `login` admin vía gateway | Token + roles `[ADMIN]` | Token emitido, `roles: ["ADMIN"]` | ✅ | Medido | `03_login_admin.txt` |
| E05 | `login` con clave incorrecta | `UNAUTHORIZED` | `Credenciales inválidas.` `UNAUTHORIZED` | ✅ | Medido | `04_login_invalido.txt` |
| E06 | Registro de ciudadano (REST) | HTTP 201, rol CONSULTOR | 201 `Usuario registrado exitosamente.` | ✅ | Medido | `05_consultor.txt` |
| E07 | CONSULTOR consulta operación ADMIN / su identidad | `FORBIDDEN` / `me` OK | `FORBIDDEN`; `me.roles = [CONSULTOR]` | ✅ | Medido | `06_consultor_denegado.txt` |
| E08 | Preflight CORS desde origen permitido y no permitido | 200 con `Access-Control-Allow-Origin` / 403 | 200 `Allow-Origin: http://localhost:4300` / 403 `Invalid CORS request` | ✅ | Medido | `07_cors.txt` |
| E09 | `radicarSolicitud` (CONSULTOR) con adjunto | PQR en estado `CREADO` | PQR creada, `estado: CREADO` | ✅ | Medido | `08_radicar.txt` |
| E10 | `cambiarEstadoSolicitud` (ADMIN) a CERRADO | `estado: CERRADO` | `estado: CERRADO` | ✅ | Medido | `09_cambiar_estado.txt` |
| E11 | `solicitud(id) { historial }` (composición de 2 llamadas REST) | Historial con CREADO y CREADO→CERRADO escrito por `sigc-audit`; `correoEnviado: true` | 2 eventos en el historial con `eventId`; `correoEnviado: true`; sin datos sensibles (sin `password`) | ✅ | Medido | `10_consulta_compuesta.txt` |
| E12 | `solicitudes` con filtro y paginación | Solo campos pedidos | `totalElementos`, `contenido{id titulo estado}` | ✅ | Medido | `11_listado.txt` |
| E13 | `misSolicitudes` (CONSULTOR) | Solo sus PQR | 1 PQR propia | ✅ | Medido | `12_mis_solicitudes.txt` |
| E14 | Mensajes en `audit-events` | 2 eventos con clave = id de la PQR y header `x-correlation-id` | `REQUEST_CREATED` y `REQUEST_STATUS_CHANGED`, misma partición, `x-correlation-id` presente | ✅ | Medido | `13_kafka_mensajes.txt` |
| E15 | Efecto de `sigc-notifications` | Notificación registrada | Notificación con estado `SIMULATED` | ✅ | Simulado (correo) | `14_notificacion.txt` |
| E16 | `resumenIndicadores` dos veces + `volumenPorEstado` | 1.ª calcula, 2.ª desde Redis (mismo `generadoEn`, más rápida); claves con TTL ≈ 30 s | Mismo `generadoEn`; 1.ª ≈ 1,3 s, 2.ª ≈ 0,05 s; 3 claves `sigc:gateway:*`, TTL 28 s | ✅ | Medido | `15_indicadores_redis.txt` |
| E17 | Trazabilidad por correlation ID | Mismo `correlationId` en gateway, API, productor y ambos consumidores | Logs `[GATEWAY]`, `[API-CLIENT]`, `[OUTBOX]`, `[KAFKA-PRODUCER]`, `[AUDITORIA]`, `[NOTIFICACIONES]` con el mismo id | ✅ | Medido | `16_trazabilidad.txt` |
| E18 | API detenida: timeout + retry | Error controlado `UNAVAILABLE` tras reintentos | 1.er intento ≈ 7 s (3 intentos) → `UNAVAILABLE` | ✅ | Simulado (fallo provocado) | `17_resiliencia.txt` |
| E19 | API detenida: circuit breaker | Tras ≥ 5 fallos se abre y responde rápido | Estado `OPEN`; intentos 3–6 en ≈ 0,02–0,04 s sin llamar a la API | ✅ | Simulado | `17_resiliencia.txt` |
| E20 | API detenida: respaldo de indicadores | Último valor con `desactualizado: true` | `desactualizado: true` | ✅ | Simulado | `17_resiliencia.txt` |
| E21 | Recuperación de la API | El gateway vuelve a responder (HALF_OPEN → CLOSED) | `solicitudes` y `resumenIndicadores` OK, `desactualizado: false` | ✅ | Medido | `18_recuperacion.txt` |
| E22 | Idempotencia ante evento duplicado | Se ignora el duplicado | Ver Entrega 2 | ✅ | Simulado | `evidencias/salidas/10_duplicado.txt`, `11_idempotencia.txt` |
| E23 | Mensajes inválidos → DLT | Van a `audit-events.DLT` y se guardan en `failed_events` | Ver Entrega 2 | ✅ | Simulado | `evidencias/salidas/12–14_*.txt` |
| E24 | Dashboard del frontend con indicadores del gateway | Tarjetas con datos de `resumenIndicadores` | Ver capturas | ✅ | Medido | `evidencias/capturas/` |

## 3. Trabajo futuro (no medido)

| Caso | Motivo |
|---|---|
| Pruebas de carga (latencia p95, throughput) | Fuera del alcance; no hay resultados medidos |
| Pruebas E2E automatizadas del frontend (Cypress/Playwright) | Pendiente |
| Envío real de correo SMTP | Requiere credenciales externas; probado solo en modo `SIMULATED` |
| Kafka con 3 brokers / tolerancia a caída del broker | Entorno de desarrollo con un broker |
