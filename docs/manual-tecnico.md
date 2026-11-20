# Manual técnico y de uso — SIGC v3.0.0

## 1. Estructura del repositorio

| Ruta | Contenido |
|---|---|
| `api.documental/` | API REST (Spring Boot). Paquetes: `controller`, `services`, `repositories`, `entities`, `dto`, `security`, `config`, `messaging/{config,event,producer,consumer}`, `exceptions` |
| `gateway/` | Gateway GraphQL (Spring Boot). `src/main/resources/graphql/schema.graphqls` es el contrato |
| `Front-Angular-ProyectoAula8voSemestre-main/` | Frontend Angular + `Dockerfile` (nginx) |
| `contracts/` | `openapi.yaml` (REST), `schema.graphql` (GraphQL), `asyncapi.yaml` y `events/request-event.schema.json` (eventos) |
| `database/sigcapi.sql` | Esquema SQL de referencia (el esquema efectivo lo mantiene JPA `ddl-auto=update`) |
| `docker-compose.yml`, `.env.example` | Infraestructura completa |
| `scripts/demo-final.sh` | Prueba de extremo a extremo y generación de evidencias (`evidencias/final/`) |
| `scripts/demo-kafka.sh` | Demostración de la Entrega 2 (idempotencia y DLT) |
| `docs/` | Arquitectura, Kafka, modelo de datos, pruebas, este manual |
| `entrega/` | Informe final FO-GI-10 y evidencias (HTML fuente + PDF) |

## 2. Instalación paso a paso

1. Instale Docker Desktop (o Docker Engine + Compose v2).
2. Descomprima el ZIP (o `git clone` y `git checkout v3.0.0`).
3. En la raíz: `cp .env.example .env` (en PowerShell: `Copy-Item .env.example .env`).
4. `docker compose up --build -d`. La primera vez descarga imágenes y dependencias (5–10 min).
5. Verifique: `docker compose ps` → `db`, `kafka`, `redis`, `api` en *healthy*; `gateway`, `frontend`, `kafka-ui` en *running*; `kafka-init` en *exited (0)*.
6. Abra http://localhost:4300 e inicie sesión con `1000000001` / `Admin.12345`.
7. (Opcional) `bash scripts/demo-final.sh` genera datos ficticios y evidencias.

## 3. Operación

| Tarea | Comando |
|---|---|
| Logs de un flujo | `docker compose logs -f gateway api \| grep "correlationId=<id>"` |
| Logs de Kafka en la API | `docker compose logs -f api \| grep -E "OUTBOX\|KAFKA\|AUDITORIA\|NOTIFICACION"` |
| Estado del gateway / API | `curl localhost:8081/actuator/health` · `curl localhost:8080/actuator/health` |
| Claves en Redis | `docker compose exec redis redis-cli --scan` |
| Tópicos y grupos | Kafka UI http://localhost:8090 o `docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --describe --all-groups` |
| Reiniciar solo un servicio | `docker compose restart gateway` |
| Reconstruir tras cambios | `docker compose up --build -d api gateway frontend` |
| Pruebas | `cd api.documental && ./mvnw test` · `cd gateway && ./mvnw test` (JDK 21) |

## 4. Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| `required variable DB_PASSWORD is missing` | No existe `.env` | `cp .env.example .env` |
| `port is already allocated` | Puerto ocupado | Cambie `API_PORT`, `GATEWAY_PORT`, `FRONTEND_PORT`, `DB_PORT`… en `.env` |
| GraphQL responde `UNAUTHORIZED` | Falta o expiró el token | Ejecute `login` y envíe `Authorization: Bearer <token>` |
| GraphQL responde `FORBIDDEN` | El usuario es CONSULTOR | Use un ADMIN |
| GraphQL responde `UNAVAILABLE` | La API no responde o el circuit breaker está abierto | `docker compose ps api`; tras recuperarse la API, el circuito se cierra en ~15 s |
| `desactualizado: true` en indicadores | Se sirvió el último valor conocido de Redis | Normal mientras la API está caída |
| El navegador muestra error CORS | El origen no está en `CORS_ALLOWED_ORIGINS` | Agréguelo en `.env` y `docker compose up -d` |

## 5. Uso del sistema (funcional)

### Ciudadano (rol CONSULTOR)
1. **Registrarse** en el frontend (o `POST /auth/register`).
2. **Iniciar sesión**; el token se guarda en el navegador y se envía en cada petición.
3. **Radicar una PQR**: título, descripción, datos de contacto, tipo (PETICIÓN/QUEJA/RECLAMO), aceptación de la política de datos y adjuntos. Vía GraphQL: `mutation radicarSolicitud`.
4. **Consultar sus PQR** (`misSolicitudes`). El acuse de radicación se registra de forma asíncrona (consumidor `sigc-notifications`).

### Administrador (rol ADMIN)
1. **Dashboard**: indicadores (total, abiertas, cerradas, eventos procesados, notificaciones, mensajes en DLT) desde el gateway, con caché Redis.
2. **Gestionar PQR**: listar con filtros (`solicitudes`), ver el detalle con el historial de auditoría (`solicitud { historial }`), cambiar el estado (`cambiarEstadoSolicitud` → CREADO / FALTA_DOCUMENTACION / CERRADO). Cada cambio genera un evento Kafka que audita y notifica al ciudadano.
3. **Usuarios, reglas y estándares**: endpoints `/admin/**` de la API REST.
4. **Observabilidad**: `/events/summary`, `/events/outbox`, `/events/dead-letters`; Kafka UI.

## 6. Documentación de la API

- REST: [contracts/openapi.yaml](../contracts/openapi.yaml) y colección Postman [API_Documental.postman_collection.json](API_Documental.postman_collection.json).
- GraphQL: [contracts/schema.graphql](../contracts/schema.graphql); explorable en http://localhost:8081/graphiql (pegue el token en *Headers*: `{"Authorization":"Bearer …"}`).
- Eventos: [contracts/asyncapi.yaml](../contracts/asyncapi.yaml) y [docs/kafka.md](kafka.md).
