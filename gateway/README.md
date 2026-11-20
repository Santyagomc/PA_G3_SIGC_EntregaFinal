# sigc-gateway — Gateway GraphQL del SIGC

Microservicio Spring Boot 4.0.3 / Java 21 (puerto **8081**). Es el punto de entrada orientado al cliente:
valida el JWT, aplica reglas de acceso por operación, compone información de varios endpoints REST y
delega toda la lógica de negocio en la API REST (`sigc-api`). **No tiene base de datos propia.**

- Endpoint: `POST http://localhost:8081/graphql` · Consola: `http://localhost:8081/graphiql`
- Contrato: `src/main/resources/graphql/schema.graphqls` (idéntico a `../contracts/schema.graphql`, verificado por `SchemaContractTest`)
- Salud: `GET /actuator/health` (incluye el estado del circuit breaker `apiRest` y de Redis)

## Operaciones y servicio REST asociado

| Operación | Acceso | Servicio REST |
|---|---|---|
| `mutation login(documento, password)` | Público | `POST /auth/login` |
| `query me` | Autenticado | (identidad del JWT, sin llamada) |
| `query misSolicitudes(page, size)` | Autenticado | `GET /requests/my` |
| `mutation radicarSolicitud(input)` | Autenticado | `POST /requests/save` (multipart; adjuntos en Base64) → la API emite `REQUEST_CREATED` en Kafka |
| `query solicitudes(filtro, page, size)` | ADMIN | `GET /requests/all` |
| `query solicitud(id) { historial }` | ADMIN | `GET /requests/findById/{id}` + `GET /requests/{id}/history` (composición) |
| `query resumenIndicadores` | ADMIN | `GET /requests/all?status=…` (×3) + `GET /events/summary` · caché Redis 30 s |
| `query volumenPorEstado` | ADMIN | `GET /requests/all?status=…` · caché Redis 30 s |
| `mutation cambiarEstadoSolicitud(id, estado, observaciones)` | ADMIN | `PUT /requests/{id}/status` → la API emite `REQUEST_STATUS_CHANGED` |

Lógica que permanece en REST: validaciones de la radicación, motor de reglas (Ley 1437/1581),
persistencia, permisos finales (la API vuelve a validar el token) y publicación de eventos Kafka.

## Seguridad
- JWT HS256 firmado por la API (`sub` = documento, `roles` = `["ADMIN"|"CONSULTOR"]`); el gateway valida firma y vigencia con el mismo secreto (`JWT_SECRET`).
- `@PreAuthorize` por resolver. Sin token → `UNAUTHORIZED`; rol insuficiente → `FORBIDDEN`.
- Hacia la API solo se propagan `Authorization` y `X-Correlation-Id`. Tokens y contraseñas nunca se registran.
- CORS explícito (`CORS_ALLOWED_ORIGINS`), solo en `/graphql`, métodos GET/POST/OPTIONS.

## Resiliencia
- **Timeout**: conexión 2 s, lectura 5 s (`API_CONNECT_TIMEOUT_MS`, `API_READ_TIMEOUT_MS`).
- **Retry**: 3 intentos con backoff exponencial (300 ms, 600 ms) **solo en consultas GET** ante red/timeout/5xx. Nunca en mutations.
- **Circuit breaker** `api-rest`: ventana de 10 llamadas, mínimo 5, umbral 50 %, abierto 15 s, 3 llamadas de prueba. Los 4xx no cuentan como fallo.
- Con la API caída: error `UNAVAILABLE` controlado; `resumenIndicadores` responde el último valor guardado en Redis con `desactualizado: true`.

## Errores
Cada error trae `extensions.codigo` (`BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `UNAVAILABLE`, `INTERNAL_ERROR`) y `extensions.correlationId`.

## Observabilidad
- `X-Correlation-Id` recibido o generado, devuelto en la respuesta, escrito en cada log (`[correlationId=…]`) y propagado a la API.
- Logs: `[GATEWAY] operacion=… resultado=OK|ERROR duracionMs=…`, `[API-CLIENT] GET /path -> status (ms)`, `[RESILIENCIA] …`, `[SEGURIDAD] …`.
- Actuator: `health`, `info`, `metrics` (`metrics` requiere ADMIN).

## Variables de entorno
| Variable | Defecto |
|---|---|
| `GATEWAY_PORT` | `8081` |
| `API_BASE_URL` | `http://localhost:8080` (Compose: `http://api:8080`) |
| `API_CONNECT_TIMEOUT_MS` / `API_READ_TIMEOUT_MS` | `2000` / `5000` |
| `JWT_SECRET` | mismo valor que la API |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200,http://localhost:4300` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `CACHE_TYPE` | `redis` (`simple` para ejecutar sin Redis) |
| `GRAPHIQL_ENABLED` | `true` |

## Pruebas
```bash
bash mvnw test      # 24 pruebas; no requieren API, Redis ni Docker
```
`GatewaySecurityGraphQlTest` (HTTP real: acceso permitido/denegado, token falsificado/expirado, CORS, correlation id, errores),
`ApiClientResilienceTest` (retry solo en GET, sin retry en mutations, apertura del circuit breaker, 4xx no abren el circuito, propagación de encabezados, contrato de respuestas de la API),
`ResolversUnitTest` (composición de indicadores, respaldo desactualizado, historial sin datos sensibles) y `SchemaContractTest`.
