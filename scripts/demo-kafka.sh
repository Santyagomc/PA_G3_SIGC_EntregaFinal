#!/usr/bin/env bash
# =============================================================================
# SIGC · Entrega 2 (P2) · Demostración del flujo orientado a eventos con Kafka
#
# Ejecuta el flujo completo contra los servicios del docker-compose y guarda cada
# salida en evidencias/salidas/NN_*.txt (insumo del PDF de evidencias).
#
# Uso (desde la raíz del proyecto, con los servicios arriba):
#   docker compose up --build -d
#   bash scripts/demo-kafka.sh
#   python scripts/generar_pdf_evidencias.py      # regenera el PDF con estas salidas
#
# Requisitos: docker compose, curl y python3 (o jq) para leer JSON.
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[ -f .env ] && set -a && . ./.env && set +a

API="${API_URL:-http://localhost:${API_PORT:-8080}}"
ADMIN_DOC="${ADMIN_DOCUMENT:-1000000001}"
ADMIN_PASS="${ADMIN_PASSWORD:-Admin.12345}"
TOPIC="${KAFKA_TOPIC_REQUEST_EVENTS:-audit-events}"
DLT="${KAFKA_TOPIC_REQUEST_EVENTS_DLT:-audit-events.DLT}"
OUT="$ROOT/evidencias/salidas"
mkdir -p "$OUT" "$ROOT/evidencias/capturas"
rm -f "$OUT"/*.txt

KT="/opt/kafka/bin"
kexec() { docker compose exec -T kafka "$@"; }

# ---- utilidades --------------------------------------------------------------
c_blue='\033[1;34m'; c_green='\033[1;32m'; c_red='\033[1;31m'; c_off='\033[0m'
step() { echo -e "\n${c_blue}== $1 ==${c_off}"; }
ok()   { echo -e "${c_green}✔ $1${c_off}"; }
fail() { echo -e "${c_red}✘ $1${c_off}"; }

# save NN_nombre "Título" comando...  → ejecuta, muestra y guarda la salida
save() {
  local file="$OUT/$1.txt"; local title="$2"; shift 2
  { echo "# $title"; echo "# $(date '+%Y-%m-%d %H:%M:%S')"; echo "\$ $*" | sed -E 's/(Bearer )[A-Za-z0-9._-]+/\1<token>/'; echo; } > "$file"
  "$@" 2>&1 | tee -a "$file"
}

# jget '.data.content[0].eventId'  (lee JSON por stdin; usa jq o python3)
jget() {
  if command -v jq >/dev/null 2>&1; then jq -r "$1"; return; fi
  local py; py="$(command -v python3 || command -v python)"
  "$py" -c '
import json,re,sys
v=json.load(sys.stdin)
for part in [p for p in sys.argv[1].split(".") if p]:
    m=re.match(r"^([^\[]*)((\[\d+\])*)$",part)
    if m.group(1): v=v.get(m.group(1)) if isinstance(v,dict) else None
    for i in re.findall(r"\[(\d+)\]",m.group(2)): v=v[int(i)] if v is not None else None
print("" if v is None else (json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v))' "$1"
}

pretty() {
  if command -v jq >/dev/null 2>&1; then jq .; else "$(command -v python3 || command -v python)" -m json.tool --no-ensure-ascii 2>/dev/null || cat; fi
}

call() { # call MÉTODO RUTA [args curl...] → imprime cuerpo formateado + código HTTP
  local m="$1" p="$2"; shift 2
  local tmp; tmp="$(mktemp)"
  local code; code=$(curl -s -o "$tmp" -w '%{http_code}' -X "$m" "$API$p" -H "Authorization: Bearer $TOKEN" "$@")
  echo "HTTP $code  $m $p"
  pretty < "$tmp"; rm -f "$tmp"
}

wait_for_api() {
  step "Esperando a la API en $API"
  for i in $(seq 1 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/auth/login" -H 'Content-Type: application/json' -d '{}')
    [ "$code" != "000" ] && ok "API disponible (HTTP $code)" && return 0
    sleep 3
  done
  fail "La API no respondió"; exit 1
}

# ---- 0. Infraestructura -------------------------------------------------------
step "0. Servicios del Docker Compose"
save 00_docker_compose_ps "Servicios levantados con Docker Compose" docker compose ps
save 00b_kafka_init_log "Log de kafka-init (creación de tópicos)" docker compose logs --no-color kafka-init
wait_for_api

step "1. Tópicos Kafka"
save 01_topicos_describe "Tópicos audit-events y audit-events.DLT" \
  kexec bash -c "$KT/kafka-topics.sh --bootstrap-server kafka:29092 --list; echo; $KT/kafka-topics.sh --bootstrap-server kafka:29092 --describe --topic $TOPIC; $KT/kafka-topics.sh --bootstrap-server kafka:29092 --describe --topic $DLT"

# ---- 2. Autenticación ----------------------------------------------------------
step "2. Login del administrador"
LOGIN=$(curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_DOC\",\"password\":\"$ADMIN_PASS\"}")
TOKEN=$(echo "$LOGIN" | jget '.token')
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then fail "No se obtuvo token. Respuesta: $LOGIN"; exit 1; fi
echo "# Login administrador → token JWT (recortado)" > "$OUT/02_login.txt"
echo "POST /auth/login → token ${TOKEN:0:25}…" | tee -a "$OUT/02_login.txt"
ok "Token obtenido"

# ---- 3. Productor: radicación ----------------------------------------------------
step "3. Radicar una PQR (productor: REQUEST_CREATED)"
SUP="$(mktemp)"; echo "Soporte de prueba Entrega 2 Kafka" > "$SUP"
REQ_JSON='{"title":"Demora en respuesta a mi petición","description":"Radiqué una petición hace 20 días y no he recibido respuesta.","fullName":"Ciudadano Demo Kafka","email":"ciudadano.demo@example.com","document":"1075000001","city":"Neiva","phone":"3001234567","type":"QUEJA","acceptedDataPolicy":true,"expirationDate":"2030-12-31"}'
RAW=$(curl -s -w '\n%{time_total}' -X POST "$API/requests/save" -H "Authorization: Bearer $TOKEN" \
  -F "request=$REQ_JSON;type=application/json" -F "files=@$SUP;type=text/plain")
rm -f "$SUP"
RESP=$(echo "$RAW" | sed '$d'); SECS=$(echo "$RAW" | tail -n 1)
REQUEST_ID=$(echo "$RESP" | jget '.data.id')
{ echo "# POST /requests/save (multipart) — respuesta en ${SECS} s, sin esperar a Kafka"; echo "$RESP" | pretty; } | tee "$OUT/03_radicar_pqr.txt"
[ -n "$REQUEST_ID" ] && ok "PQR radicada id=$REQUEST_ID" || { fail "No se radicó la PQR"; exit 1; }

# ---- 4. Productor: cambio de estado ------------------------------------------------
step "4. Cambiar estado a CERRADO (productor: REQUEST_STATUS_CHANGED)"
save 04_cambiar_estado "PUT /requests/$REQUEST_ID/status?status=CERRADO" \
  call PUT "/requests/$REQUEST_ID/status?status=CERRADO&observations=Respuesta%20de%20fondo%20enviada%20al%20ciudadano"

echo "Esperando 6 s a que el relay publique y los consumidores procesen…"; sleep 6

# ---- 5. Evidencias del productor ---------------------------------------------------
step "5. Outbox: eventos PUBLISHED con partición y offset"
save 05_outbox "GET /events/outbox?requestId=$REQUEST_ID" call GET "/events/outbox?requestId=$REQUEST_ID"
EVENT_ID=$(curl -s "$API/events/outbox?requestId=$REQUEST_ID&size=1" -H "Authorization: Bearer $TOKEN" | jget '.data.content[0].eventId')
ok "eventId del cambio de estado: $EVENT_ID"

step "6. Mensajes en el tópico (clave, partición, headers y JSON)"
save 06_mensajes_topico "kafka-console-consumer sobre $TOPIC" \
  kexec $KT/kafka-console-consumer.sh --bootstrap-server kafka:29092 --topic "$TOPIC" --from-beginning \
  --property print.key=true --property print.partition=true --property print.offset=true --property print.headers=true \
  --timeout-ms 8000

# ---- 7. Evidencias de los consumidores ---------------------------------------------
step "7. Consumidor sigc-audit → historial"
save 07_historial "GET /requests/$REQUEST_ID/history" call GET "/requests/$REQUEST_ID/history"

step "8. Consumidor sigc-notifications → notificaciones"
save 08_notificaciones "GET /events/notifications?requestId=$REQUEST_ID" call GET "/events/notifications?requestId=$REQUEST_ID"
save 08b_mail_status "GET /requests/findById/$REQUEST_ID (mailStatus=true)" call GET "/requests/findById/$REQUEST_ID"

step "9. Grupos de consumidores y lag"
save 09_consumer_groups "kafka-consumer-groups --describe" \
  kexec bash -c "$KT/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --list; echo; for g in sigc-audit sigc-notifications sigc-dlt; do $KT/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --describe --group \$g; echo; done"

# ---- 8. Idempotencia --------------------------------------------------------------
step "10. Idempotencia: reenvío del mismo evento"
HIST_ANTES=$(curl -s "$API/requests/$REQUEST_ID/history" -H "Authorization: Bearer $TOKEN" | jget '.data.totalElements')
NOTI_ANTES=$(curl -s "$API/events/notifications?requestId=$REQUEST_ID" -H "Authorization: Bearer $TOKEN" | jget '.data.totalElements')
save 10_duplicado "POST /events/simulate/duplicate/$EVENT_ID" call POST "/events/simulate/duplicate/$EVENT_ID"
sleep 5
HIST_DESP=$(curl -s "$API/requests/$REQUEST_ID/history" -H "Authorization: Bearer $TOKEN" | jget '.data.totalElements')
NOTI_DESP=$(curl -s "$API/events/notifications?requestId=$REQUEST_ID" -H "Authorization: Bearer $TOKEN" | jget '.data.totalElements')
{
  echo "# Verificación de idempotencia"
  echo "Historial      antes=$HIST_ANTES  después=$HIST_DESP"
  echo "Notificaciones antes=$NOTI_ANTES  después=$NOTI_DESP"
  echo
  echo "# Logs de la API (duplicado detectado por cada consumidor)"
  docker compose logs --no-color --since 2m api | grep -E "duplicado|SIMULADOR" | tail -n 10
} | tee "$OUT/11_idempotencia.txt"
[ "$HIST_ANTES" = "$HIST_DESP" ] && [ "$NOTI_ANTES" = "$NOTI_DESP" ] && ok "El duplicado no produjo efectos" || fail "Revise la idempotencia"

# ---- 9. Errores y DLT --------------------------------------------------------------
step "11. Manejo de errores: mensajes al DLT"
save 12_simulaciones_error "Simulación de mensajes no procesables" bash -c "
$(declare -f call pretty); API='$API'; TOKEN='$TOKEN'
call POST /events/simulate/invalid-payload; echo
call POST /events/simulate/contract-violation; echo
call POST /events/simulate/unknown-request"
sleep 6
save 13_dead_letters "GET /events/dead-letters" call GET "/events/dead-letters?size=10"
save 14_mensajes_dlt "kafka-console-consumer sobre $DLT (headers con la causa)" \
  kexec $KT/kafka-console-consumer.sh --bootstrap-server kafka:29092 --topic "$DLT" --from-beginning \
  --property print.key=true --property print.partition=true --property print.headers=true --timeout-ms 8000

# ---- 10. Resumen y logs ----------------------------------------------------------
step "12. Resumen del flujo"
save 15_resumen "GET /events/summary" call GET "/events/summary"
save 16_logs_api "Logs de la API (productor, consumidores, DLT)" \
  bash -c "docker compose logs --no-color --since 10m api | grep -E 'OUTBOX|KAFKA-PRODUCER|KAFKA-CONSUMER|AUDITORIA|NOTIFICACION|KAFKA-DLT|KAFKA-RETRY|SIMULADOR' | tail -n 60"

echo
ok "Demostración completa. Salidas guardadas en evidencias/salidas/"
echo "   Kafka UI: http://localhost:${KAFKA_UI_PORT:-8090}  (tópicos, mensajes y grupos)"
echo "   Siguiente paso: python3 scripts/generar_pdf_evidencias.py"
