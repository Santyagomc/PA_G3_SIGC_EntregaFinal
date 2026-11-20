# Decisiones técnicas pendientes (actualizado — Entrega 2, segundo periodo)

> **Actualización (Entrega 2 – Parte 2, Kafka):** la **brecha 6 quedó resuelta**. Se implementó
> el tópico `audit-events` (3 particiones, clave `requestId`) con su DLT `audit-events.DLT`, un
> productor con patrón *Transactional Outbox* y tres consumidores (`sigc-audit`, que escribe
> `request_history`; `sigc-notifications`, que notifica al ciudadano; `sigc-dlt`, que guarda los
> mensajes fallidos). Detalle en `docs/kafka.md` y `contracts/asyncapi.yaml`.
>
> Decisiones tomadas en esta entrega:
>
> | Decisión | Alternativa descartada | Motivo |
> |---|---|---|
> | Transactional Outbox + relay | `KafkaTemplate.send()` en `@TransactionalEventListener(AFTER_COMMIT)` | Si Kafka está caído tras el commit, el evento se perdería. |
> | Consumidores dentro del mismo backend | Microservicio de auditoría aparte | Coherente con la arquitectura aprobada; los grupos de consumidores ya aíslan su avance. |
> | Mover el correo a un consumidor | Mantener `@Async` | `@Async` no estaba activo (faltaba `@EnableAsync`) y no ofrece reintentos ni DLT. |
> | Evento sin datos personales | Evento con correo/nombre | Ley 1581: no replicar datos personales en el log de Kafka. |
> | Un solo tópico para los dos tipos de evento | Un tópico por tipo | Mantener el orden relativo de los eventos de la misma PQR. |


> **Actualización (Entrega 1 – Parte 2):** la brecha 1 quedó resuelta con la migración a **JWT
> (HMAC)**: `POST /auth/login` entrega el token y las rutas protegidas exigen
> `Authorization: Bearer <token>`. La brecha 5 quedó resuelta parcialmente: `GET /requests/my`
> está implementado y documentado en `contracts/openapi.yaml` (`GET /documents/my` sigue
> pendiente). La brecha 6: la tabla `request_history` y su servicio existen en la API, pero la
> mensajería con Kafka no se incluye en esta entrega. El registro público ya no permite crear administradores: fuerza el rol CONSULTOR, y el administrador inicial se crea con variables de entorno. Las demás brechas siguen abiertas.


Al contrastar la propuesta de arquitectura con el backend ya
implementado (`sigcapi.sql` + `SIGC.postman_collection.json`), se
identifican las siguientes brechas y decisiones pendientes para la
siguiente fase de implementación:

| # | Brecha detectada | Decisión pendiente |
|---|---|---|
| 1 | El backend actual autentica por sesión de servidor (cookie `JSESSIONID`), no con JWT. | Definir si se migra a JWT HMAC-SHA256 como se propuso en la Entrega 1, o si se mantiene la sesión de servidor y se documenta como decisión definitiva. |
| 2 | La tabla `sesiones` no tiene `request_id`: una sesión de chatbot no queda asociada a una solicitud PQR. | Agregar la columna `request_id` (FK) si se requiere trazabilidad chatbot-solicitud, tal como describe RF-12. |
| 3 | `requests` no tiene `user_id`; la relación con el usuario se resuelve por coincidencia de `document`. | Evaluar agregar la FK `user_id` para integridad referencial, o mantener el match por documento si es una decisión de diseño intencional. |
| 4 | La tabla `chats` existe en BD pero no tiene endpoints expuestos en el Postman collection actual. | Definir y documentar los endpoints de mensajería (`GET/POST /sessions/{id}/chats` o similar) en la siguiente entrega. |
| 5 | No existen aún `GET /requests/my` ni `GET /documents/my` (autoservicio del ciudadano). | Implementar en el backend; el contrato OpenAPI ya los deja definidos como propuesta. |
| 6 | ~~No existe la tabla `request_history` ni infraestructura Kafka en el backend actual.~~ | **Resuelta en la Entrega 2 (P2)**: ver `docs/kafka.md`. |
| 7 | `users.status` (ACTIVO/BLOQUEADO/INACTIVO) convive con `failed_attempts`/`lock_until`; el registro recibe un booleano `admin` en vez de un rol explícito. | Confirmar si el mapeo `admin: boolean -> rol: ADMIN/CONSULTOR` se mantiene o se expone `rol` directamente en el contrato. |

Estas decisiones deben resolverse con el equipo y, cuando aplique,
con la retroalimentación del docente antes de avanzar a la
implementación completa de GraphQL y Kafka.
