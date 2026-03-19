#!/usr/bin/env bash
set -euo pipefail

# ── Colours & helpers ─────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BOLD='\033[1m'; NC='\033[0m'

PASS=0; FAIL=0

pass() { echo -e "${GREEN}  ✓${NC} $*"; ((PASS++)); }
fail() { echo -e "${RED}  ✗${NC} $*"; ((FAIL++)); }
section() { echo -e "\n${BOLD}$*${NC}"; }

BASE_URL="http://localhost:8080"
TICKET_SVC="http://localhost:8081"
AI_SVC="http://localhost:8000"

# ── Helper: assert JSON field equals expected value ───────────────
assert_field() {
  local label=$1 json=$2 field=$3 expected=$4
  local actual
  actual=$(echo "$json" | grep -o "\"$field\":\"[^\"]*\"" | cut -d'"' -f4 || true)
  if [ "$actual" = "$expected" ]; then
    pass "$label: $field = $actual"
  else
    fail "$label: expected $field='$expected', got '$actual'"
  fi
}

assert_not_empty() {
  local label=$1 value=$2
  if [ -n "$value" ] && [ "$value" != "null" ]; then
    pass "$label: not empty ($value)"
  else
    fail "$label: expected non-empty value"
  fi
}

# ═══════════════════════════════════════════════════════════════════
section "1. Health Checks"
# ═══════════════════════════════════════════════════════════════════

check_health() {
  local name=$1 url=$2
  status=$(curl -sf "$url" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "DOWN")
  if [ "$status" = "UP" ]; then
    pass "$name health: UP"
  else
    fail "$name health: $status"
  fi
}

check_health "ticket-service"       "$TICKET_SVC/actuator/health"
check_health "orchestrator-service" "http://localhost:8082/actuator/health"
check_health "notification-service" "http://localhost:8083/actuator/health"

ai_health=$(curl -sf "$AI_SVC/health" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "DOWN")
if [ "$ai_health" = "up" ]; then
  pass "ai-enrichment health: up"
else
  fail "ai-enrichment health: $ai_health"
fi

# ═══════════════════════════════════════════════════════════════════
section "2. Create Ticket (POST /api/tickets)"
# ═══════════════════════════════════════════════════════════════════

CREATE_RESPONSE=$(curl -sf -X POST "$TICKET_SVC/api/tickets" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Cannot access account after password reset",
    "description": "I reset my password yesterday but now I get error 403 on login. This is urgent."
  }')

TICKET_ID=$(echo "$CREATE_RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || true)

assert_not_empty  "Create ticket" "$TICKET_ID"
assert_field      "Create ticket" "$CREATE_RESPONSE" "status" "OPEN"

echo "  → Ticket ID: $TICKET_ID"

# ═══════════════════════════════════════════════════════════════════
section "3. AI Enrichment Direct Call (POST /enrich)"
# ═══════════════════════════════════════════════════════════════════

ENRICH_RESPONSE=$(curl -sf -X POST "$AI_SVC/enrich" \
  -H "Content-Type: application/json" \
  -d "{
    \"ticket_id\": \"$TICKET_ID\",
    \"title\": \"Cannot access account after password reset\",
    \"description\": \"I reset my password yesterday but now I get error 403 on login.\"
  }")

AI_CATEGORY=$(echo "$ENRICH_RESPONSE" | grep -o '"category":"[^"]*"' | cut -d'"' -f4 || true)
AI_PRIORITY=$(echo "$ENRICH_RESPONSE" | grep -o '"priority":"[^"]*"' | cut -d'"' -f4 || true)
AI_SUMMARY=$(echo "$ENRICH_RESPONSE" | grep -o '"summary":"[^"]*"' | cut -d'"' -f4 || true)

assert_not_empty "AI enrichment" "$AI_CATEGORY"
assert_not_empty "AI enrichment" "$AI_PRIORITY"
assert_not_empty "AI enrichment" "$AI_SUMMARY"

echo "  → Category:  $AI_CATEGORY"
echo "  → Priority:  $AI_PRIORITY"
echo "  → Summary:   $AI_SUMMARY"

# ═══════════════════════════════════════════════════════════════════
section "4. Kafka Event Flow — Wait for Async Enrichment"
# ═══════════════════════════════════════════════════════════════════

echo "  Waiting up to 15s for orchestrator to process TicketCreatedEvent..."

ENRICHED=false
for i in $(seq 1 15); do
  sleep 1
  TICKET_STATE=$(curl -sf "$TICKET_SVC/api/tickets/$TICKET_ID" || echo "{}")
  STATUS=$(echo "$TICKET_STATE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || true)
  if [ "$STATUS" = "ENRICHED" ] || [ "$STATUS" = "NEEDS_MANUAL_REVIEW" ]; then
    ENRICHED=true
    break
  fi
  echo -n "."
done
echo ""

if [ "$ENRICHED" = "true" ]; then
  FINAL_STATUS=$(echo "$TICKET_STATE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
  if [ "$FINAL_STATUS" = "ENRICHED" ]; then
    pass "Async flow: ticket status = ENRICHED (AI enrichment applied)"
    AI_CAT=$(echo "$TICKET_STATE" | grep -o '"aiCategory":"[^"]*"' | cut -d'"' -f4 || true)
    AI_PRI=$(echo "$TICKET_STATE" | grep -o '"aiPriority":"[^"]*"' | cut -d'"' -f4 || true)
    assert_not_empty "Kafka flow aiCategory" "$AI_CAT"
    assert_not_empty "Kafka flow aiPriority" "$AI_PRI"
  else
    pass "Async flow: ticket status = $FINAL_STATUS (DLT fallback triggered — manual review)"
  fi
else
  fail "Async flow: ticket still OPEN after 15s — check orchestrator logs"
  echo "  → docker compose logs orchestrator-service"
fi

# ═══════════════════════════════════════════════════════════════════
section "5. Metrics Endpoints"
# ═══════════════════════════════════════════════════════════════════

for svc_url in \
  "ticket-service|$TICKET_SVC/actuator/prometheus" \
  "orchestrator-service|http://localhost:8082/actuator/prometheus"; do
  name=$(echo "$svc_url" | cut -d'|' -f1)
  url=$(echo "$svc_url" | cut -d'|' -f2)
  if curl -sf "$url" | grep -q "jvm_memory"; then
    pass "$name: Prometheus metrics available"
  else
    fail "$name: Prometheus metrics not available"
  fi
done

# ═══════════════════════════════════════════════════════════════════
section "6. Validation — Bad Request Rejection"
# ═══════════════════════════════════════════════════════════════════

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$TICKET_SVC/api/tickets" \
  -H "Content-Type: application/json" \
  -d '{"title": ""}')

if [ "$HTTP_STATUS" = "400" ]; then
  pass "Validation: empty title rejected with 400"
else
  fail "Validation: expected 400, got $HTTP_STATUS"
fi

# ═══════════════════════════════════════════════════════════════════
section "Results"
# ═══════════════════════════════════════════════════════════════════

TOTAL=$((PASS + FAIL))
echo ""
echo -e "  ${BOLD}$PASS / $TOTAL tests passed${NC}"

if [ $FAIL -gt 0 ]; then
  echo -e "  ${RED}$FAIL test(s) failed${NC}"
  echo ""
  echo "  Useful debug commands:"
  echo "    docker compose logs ticket-service"
  echo "    docker compose logs orchestrator-service"
  echo "    docker compose logs ai-enrichment"
  exit 1
else
  echo -e "  ${GREEN}All tests passed!${NC}"
  echo ""
  echo "  Next steps:"
  echo "    → Grafana dashboard: http://localhost:3000"
  echo "    → Swagger UI (AI):   http://localhost:8000/docs"
  echo "    → Kafka topics:      docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092"
fi
