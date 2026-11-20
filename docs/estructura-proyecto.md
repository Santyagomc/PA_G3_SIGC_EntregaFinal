# Estructura de proyectos / repositorios

> **Estado de implementación (Entrega 2 – Parte 2):** Kafka **ya está implementado** dentro de
> `api.documental` en el paquete `com.backend.sigcapi.messaging` (`config/`, `event/`, `producer/`,
> `consumer/`), y `docker-compose.yml` levanta `db`, `kafka`, `kafka-init`, `kafka-ui` y `api`.
> El gateway GraphQL sigue fuera del alcance.
>
> **Estado anterior (Entrega 1 – Parte 2):** este documento describe la
> arquitectura objetivo propuesta. Lo implementado y entregado hoy es la API REST
> (`api.documental/`, Spring Boot 4 / Java 21 / MariaDB) con **autenticación JWT (Bearer)**
> —no sesión `JSESSIONID`—, y el frontend Angular de referencia. El `gateway` GraphQL y
> Kafka **no** forman parte de esta entrega. Donde este documento menciona `JSESSIONID`,
> léase "token JWT en el encabezado `Authorization`".


Se adopta un **monorepo** con tres subproyectos independientes —
`api` (REST), `gateway` (GraphQL) y `frontend` — orquestados en
conjunto por `docker-compose.yml` en la raíz. `api` y `gateway` son
**microservicios separados**, cada uno con su propio proceso,
despliegue y `Dockerfile`: el `gateway` no tiene datasource propio ni
comparte `Services`/`SecurityConfig` con la `api`, solo la consume
por HTTP interno. Esto coincide con los `build: ./api`,
`build: ./gateway` y `build: ./frontend` documentados (hoy
comentados, ver nota más abajo) en el Docker Compose.

> **Nota sobre el estado de esta entrega:** el código de `api/`,
> `gateway/` y `frontend/` aún no se entrega — el alcance de la
> Entrega 2 son los contratos y las decisiones técnicas (ver
> `README.md`). El árbol siguiente documenta la estructura objetivo
> que se implementará a partir de la Entrega 3.

```
sigc/
├── docker-compose.yml
├── .env.example
├── README.md
├── contracts/
│   ├── openapi.yaml
│   └── schema.graphql
├── docs/
│   ├── kafka.md
│   ├── modelo-datos.md
│   └── estructura-proyecto.md
├── api/                           # Java 21 + Spring Boot 4 (Maven) — microservicio REST
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/sigc/api/
│       ├── controller/            # Controladores REST
│       ├── services/              # Reglas de negocio
│       ├── repositories/          # Spring Data JPA
│       ├── entities/              # Entidades JPA (users, requests, ...)
│       ├── dto/                   # DTOs de entrada/salida
│       ├── security/              # SecurityConfig (sesión JSESSIONID)
│       ├── messaging/             # Producer/Consumer de Kafka (AuditEvent)
│       ├── config/                # Configuración general
│       ├── mapper/                # Entity <-> DTO
│       ├── util/
│       └── exceptions/
├── gateway/                       # Java 21 + Spring Boot 4 (Maven) — microservicio Gateway GraphQL
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/sigc/gateway/
│       ├── graphql/               # Resolvers GraphQL (dashboard de indicadores)
│       ├── client/                # Cliente HTTP hacia la api (reenvía cookie JSESSIONID)
│       └── config/
└── frontend/                      # Angular 20 (Standalone Components)
    ├── package.json
    ├── Dockerfile
    └── src/app/
        ├── auth/
        ├── requests/
        ├── documents/
        ├── chatbot/
        ├── admin/
        └── dashboard/             # Consumo del endpoint /graphql (vía gateway)
```

## Convenciones

- `api` y `gateway` son **microservicios independientes**: cada uno
  se compila, versiona y despliega por separado. El `gateway` no
  tiene datasource propio y nunca accede a la base de datos ni a los
  `Services` de la `api` directamente — sus resolvers agregan una
  respuesta a partir de llamadas HTTP internas a la `api`, reenviando
  la cookie de sesión (`JSESSIONID`) en cada llamada.
- El productor y el consumidor de Kafka viven dentro del módulo
  `api` (no hay un microservicio de auditoría aparte en esta
  entrega).
- Cada módulo (`api`, `gateway`, `frontend`) tiene su propio
  `Dockerfile`; `docker-compose.yml` en la raíz los integra junto con
  `db` y `kafka`. En esta entrega esos tres servicios están
  comentados en el Compose porque su código todavía no existe; `db` y
  `kafka` sí están activos y son construibles/ejecutables hoy.
- La autenticación actual es por sesión de servidor (`JSESSIONID`),
  no JWT. La migración a JWT es una decisión técnica pendiente (ver
  `docs/decisiones-pendientes.md`); si se aprueba, seguirá aplicando
  solo dentro de `api/security/`, sin que el `gateway` gestione
  tokens propios.
