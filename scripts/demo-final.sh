#!/usr/bin/env bash
# =============================================================================
# SIGC · Entrega final (P3) · Demostración de extremo a extremo
#
# Recorre el flujo principal a través del GATEWAY GraphQL y guarda cada salida en
# evidencias/final/NN_*.txt:
#   servicios → salud → seguridad (401/403/CORS) → login → radicar PQR → cambiar estado
#   → Kafka (auditoría/notificación) → consulta compuesta con historial → indicadores (Redis)
#   → trazabilidad por correlation ID → resiliencia (API caída: timeout, retry, circuit breaker,
#   respuesta desactualizada desde Redis) → recuperación.
#
# Uso (desde la raíz, con los servicios arriba):
#   docker compose up --build -d
#   bash scripts/demo-final.sh
# Nota: los textos enviados no llevan tildes porque curl en Git Bash (Windows) no siempre los envía en UTF-8.
# Requisitos: bash, curl y docker compose (no requiere jq ni python).
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash (Windows): no convertir rutas como /opt/kafka/... a C:/Program Files/Git/...

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[ -f .env ] && set -a && . ./.env && set +a

API="http://localhost:${API_PORT:-8080}"
GW="http://localhost:${GATEWAY_PORT:-8081}/graphql"
GW_BASE="http://localhost:${GATEWAY_PORT:-8081}"
ADMIN_DOC="${ADMIN_DOCUMENT:-1000000001}"
ADMIN_PASS="${ADMIN_PASSWORD:-Admin.12345}"
TOPIC="${KAFKA_TOPIC_REQUEST_EVENTS:-audit-events}"
OUT="$ROOT/evidencias/final"
mkdir -p "$OUT"
rm -f "$OUT"/*.txt
CID="demo-final-$(date +%H%M%S)"   # identificador de correlación de este recorrido

c_blue='\033[1;34m'; c_off='\033[0m'
step() { echo -e "\n${c_blue}== $1 ==${c_off}"; }

# save NN_nombre "Título" comando...  → ejecuta, muestra y guarda la salida (sin tokens)
save() {
  local file="$OUT/$1.txt"; local title="$2"; shift 2
  step "$title"
  { echo "# $title"; echo "# $(date '+%Y-%m-%d %H:%M:%S')"; echo; } > "$file"
  "$@" 2>&1 | sed -E 's/(Bearer |"token":")[A-Za-z0-9._-]{20,}/\1<token-omitido>/g' | tee -a "$file"
}

# gql TOKEN 'consulta' [correlationId]  → POST al gateway, imprime código HTTP y cuerpo
gql() {
  local token="$1" query="$2" cid="${3:-$CID}"
  local body; body=$(printf '{"query":"%s"}' "$(echo "$query" | sed 's/"/\\"/g' | tr '\n' ' ')")
  local auth=(); [ -n "$token" ] && auth=(-H "Authorization: Bearer $token")
  echo "> $query" | tr -s ' '
  curl -s -w '\n[HTTP %{http_code} · %{time_total}s]\n' -X POST "$GW" -H 'Content-Type: application/json' \
       -H "X-Correlation-Id: $cid" "${auth[@]}" -d "$body"
  echo
}
jfield() { sed -nE "s/.*\"$1\":\"?([^\",}]*)\"?.*/\1/p" | head -1; }

wait_http() { # wait_http URL nombre
  for i in $(seq 1 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$1")
    [ "$code" = "200" ] && echo "$2 disponible" && return 0
    sleep 3
  done
  echo "$2 no respondió"; return 1
}

step "Esperando servicios"
wait_http "$API/actuator/health" "API REST"
wait_http "$GW_BASE/actuator/health" "Gateway GraphQL"

save 00_servicios "Contenedores de la arquitectura (docker compose ps)" \
  docker compose ps --format 'table {{.Service}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'

save 01_salud "Salud de la API y del gateway (Actuator)" bash -c "
  echo 'GET $API/actuator/health'; curl -s $API/actuator/health; echo;
  echo 'GET $GW_BASE/actuator/health'; curl -s $GW_BASE/actuator/health; echo"

# ---------------------------------------------------------------- Seguridad
save 02_sin_token "Seguridad: operación protegida SIN token → UNAUTHORIZED" \
  gql "" 'query { resumenIndicadores { totalSolicitudes } }'

save 03_login_admin "Mutation login (pública) del administrador vía gateway" \
  gql "" "mutation { login(documento: \"$ADMIN_DOC\", password: \"$ADMIN_PASS\") { documento roles mensaje token } }"
TOKEN=$(gql "" "mutation { login(documento: \"$ADMIN_DOC\", password: \"$ADMIN_PASS\") { token } }" | jfield token)
[ -z "$TOKEN" ] && echo "No se obtuvo token del administrador" && exit 1

save 04_login_invalido "Seguridad: credenciales inválidas → UNAUTHORIZED" \
  gql "" "mutation { login(documento: \"$ADMIN_DOC\", password: \"incorrecta\") { token } }"

CONS_DOC="9$(date +%H%M%S)01"
save 05_consultor "Registro de un CONSULTOR (REST) y acceso DENEGADO a una operación ADMIN → FORBIDDEN" bash -c "
  echo 'POST $API/auth/register'
  curl -s -w '\n[HTTP %{http_code}]\n' -X POST $API/auth/register -H 'Content-Type: application/json' \
    -d '{\"name\":\"Ciudadana Demo\",\"email\":\"ciudadana.$CONS_DOC@example.com\",\"document\":\"$CONS_DOC\",\"password\":\"Demo.12345\"}'"
CTOKEN=$(gql "" "mutation { login(documento: \"$CONS_DOC\", password: \"Demo.12345\") { token } }" | jfield token)
save 06_consultor_denegado "Seguridad: CONSULTOR consulta indicadores (ADMIN) → FORBIDDEN; consulta su identidad → OK" bash -c "
  $(declare -f gql); GW='$GW'; CID='$CID'
  gql '$CTOKEN' 'query { resumenIndicadores { totalSolicitudes } }'
  gql '$CTOKEN' 'query { me { documento roles } }'"

save 07_cors "CORS explícito: origen permitido vs. origen no permitido (preflight)" bash -c "
  echo '--- Origin: http://localhost:4300 (permitido)'
  curl -s -i -X OPTIONS $GW -H 'Origin: http://localhost:4300' -H 'Access-Control-Request-Method: POST' \
       -H 'Access-Control-Request-Headers: authorization,content-type' | grep -iE '^HTTP|access-control'
  echo '--- Origin: http://evil.example (no permitido)'
  curl -s -i -X OPTIONS $GW -H 'Origin: http://evil.example' -H 'Access-Control-Request-Method: POST' | grep -iE '^HTTP|access-control|invalid'"

# ---------------------------------------------------------------- Flujo principal
ADJ=$(printf 'Soporte de la PQR radicada desde el gateway GraphQL' | base64 | tr -d '\n')
VENCE=$(date -d '+15 days' +%Y-%m-%d 2>/dev/null || date -v+15d +%Y-%m-%d)
RADICAR="mutation { radicarSolicitud(input: { titulo: \"Demora en respuesta de peticion\", descripcion: \"Radicada via gateway GraphQL para la demostracion final\", nombreCompleto: \"Ciudadana Demo\", correo: \"ciudadana.$CONS_DOC@example.com\", documento: \"$CONS_DOC\", ciudad: \"Neiva\", telefono: \"3000000000\", tipo: QUEJA, aceptaPoliticaDatos: true, fechaVencimiento: \"$VENCE\", adjuntos: [{ nombre: \"soporte.txt\", contenidoBase64: \"$ADJ\", tipoMime: \"text/plain\" }] }) { id titulo tipo estado documento creadaEn } }"
save 08_radicar "Mutation radicarSolicitud (CONSULTOR) → API REST POST /requests/save → evento REQUEST_CREATED" \
  gql "$CTOKEN" "$RADICAR"
RID=$(grep -oE '"id":"[0-9]+"' "$OUT/08_radicar.txt" | head -1 | grep -oE '[0-9]+')
[ -z "$RID" ] && echo "No se obtuvo el id de la PQR" && exit 1

save 09_cambiar_estado "Mutation cambiarEstadoSolicitud (ADMIN) → PUT /requests/$RID/status → evento REQUEST_STATUS_CHANGED" \
  gql "$TOKEN" "mutation { cambiarEstadoSolicitud(id: \"$RID\", estado: CERRADO, observaciones: \"Respuesta de fondo enviada\") { id estado } }"

sleep 5   # tiempo para que el relay publique y los consumidores procesen

save 10_consulta_compuesta "Query solicitud(id) con historial: composición de 2 llamadas REST; el historial lo escribió el consumidor Kafka sigc-audit" \
  gql "$TOKEN" "query { solicitud(id: \"$RID\") { id titulo estado tipo correoEnviado historial { estadoAnterior estadoNuevo actor eventId observaciones cambiadoEn } } }"

save 11_listado "Query solicitudes (ADMIN) con filtro y paginación; el cliente pide solo los campos que necesita" \
  gql "$TOKEN" 'query { solicitudes(filtro: { estado: CERRADO }, page: 0, size: 3) { totalElementos totalPaginas contenido { id titulo estado } } }'

save 12_mis_solicitudes "Query misSolicitudes (CONSULTOR)" \
  gql "$CTOKEN" 'query { misSolicitudes { totalElementos contenido { id titulo estado } } }'

save 13_kafka_mensajes "Mensajes de la PQR $RID en el tópico $TOPIC con headers (x-correlation-id)" bash -c "
  docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
    --topic $TOPIC --from-beginning --timeout-ms 8000 --property print.headers=true --property print.key=true \
    --property print.partition=true 2>/dev/null | grep '\"requestId\":$RID,'"

save 14_notificacion "Efecto del consumidor sigc-notifications (REST /events/notifications)" bash -c "
  curl -s '$API/events/notifications?requestId=$RID' -H 'Authorization: Bearer $TOKEN'; echo"

# ---------------------------------------------------------------- Redis
docker compose exec -T redis redis-cli FLUSHALL >/dev/null
save 15_indicadores_redis "Query resumenIndicadores: 1.ª llamada calcula (miss), 2.ª sale de Redis (hit); claves y TTL en Redis" bash -c "
  $(declare -f gql); GW='$GW'; CID='$CID'
  gql '$TOKEN' 'query { resumenIndicadores { totalSolicitudes solicitudesAbiertas solicitudesCerradas eventosProcesados notificaciones mensajesDlt generadoEn desactualizado } }'
  gql '$TOKEN' 'query { resumenIndicadores { totalSolicitudes generadoEn desactualizado } }'
  gql '$TOKEN' 'query { volumenPorEstado { estado cantidad } }'
  echo '--- redis-cli KEYS *'; docker compose exec -T redis redis-cli --scan
  for k in \$(docker compose exec -T redis redis-cli --scan | tr -d '\r'); do echo \"TTL \$k = \$(docker compose exec -T redis redis-cli TTL \"\$k\" | tr -d '\r') s\"; done"

# ---------------------------------------------------------------- Observabilidad
save 16_trazabilidad "Trazabilidad: logs de gateway, API, productor y consumidores con correlationId=$CID" bash -c "
  docker compose logs --no-color gateway api 2>/dev/null | grep -F '$CID' | grep -vE 'Bearer' | cut -c1-260 | tail -40"

# ---------------------------------------------------------------- Resiliencia
save 17_resiliencia "Resiliencia: API REST detenida → timeout/retry/circuit breaker en el gateway" bash -c "
  $(declare -f gql); GW='$GW'
  docker compose stop api
  for i in 1 2 3 4 5 6; do echo \"--- intento \$i\"; gql '$TOKEN' 'query { solicitudes(page: 0, size: 1) { totalElementos } }' resiliencia-\$i | tail -3 | head -2; done
  echo '--- Indicadores con la API caída (se expira la caché de 30 s para forzar el respaldo): último valor conocido desde Redis'
  docker compose exec -T redis redis-cli DEL 'sigc:gateway:resumenIndicadores::global' >/dev/null
  gql '$TOKEN' 'query { resumenIndicadores { totalSolicitudes generadoEn desactualizado } }' resiliencia-cache
  echo '--- Estado del gateway'; curl -s $GW_BASE/actuator/health; echo
  echo '--- Logs [RESILIENCIA] del gateway'; docker compose logs --no-color gateway | grep -E 'RESILIENCIA|CircuitBreaker' | cut -c1-220 | tail -12"

save 18_recuperacion "Recuperación: se inicia la API y el gateway vuelve a responder (circuit breaker HALF_OPEN → CLOSED)" bash -c "
  $(declare -f gql); GW='$GW'
  docker compose start api
  for i in \$(seq 1 40); do c=\$(curl -s -o /dev/null -w '%{http_code}' $API/actuator/health); [ \"\$c\" = 200 ] && break; sleep 3; done
  echo 'API disponible de nuevo'; sleep 16
  gql '$TOKEN' 'query { solicitudes(page: 0, size: 1) { totalElementos } }' recuperacion-1
  gql '$TOKEN' 'query { resumenIndicadores { totalSolicitudes desactualizado } }' recuperacion-2"

step "Listo. Evidencias en evidencias/final/ (correlationId del recorrido: $CID)"
