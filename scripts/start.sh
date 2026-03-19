#!/usr/bin/env bash
set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Pre-flight checks ─────────────────────────────────────────────
command -v docker   >/dev/null 2>&1 || error "Docker not found. Install Docker Desktop first."
command -v curl     >/dev/null 2>&1 || error "curl not found."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$ROOT_DIR"

# ── .env setup ───────────────────────────────────────────────────
if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    warn ".env not found — copying from .env.example"
    cp .env.example .env
    warn "Edit .env and add your AZURE_OPENAI_API_KEY, then re-run this script."
    warn "  → To run offline without Azure: set OLLAMA_MODE=true in .env"
    exit 1
  else
    error ".env.example missing. Please re-clone the repository."
  fi
fi

# Check if Azure key is set or Ollama mode is active
source .env
if [ "${OLLAMA_MODE:-false}" = "false" ] && [ -z "${AZURE_OPENAI_API_KEY:-}" ]; then
  warn "AZURE_OPENAI_API_KEY is not set in .env"
  warn "Options:"
  warn "  1. Add your Azure OpenAI key to .env"
  warn "  2. Set OLLAMA_MODE=true for offline mode (requires: ollama pull llama3.2)"
  exit 1
fi

# ── Build & start ─────────────────────────────────────────────────
info "Building and starting all services..."
docker compose up --build -d

# ── Wait for services ─────────────────────────────────────────────
info "Waiting for services to be healthy..."

wait_for() {
  local name=$1 url=$2 retries=30
  for i in $(seq 1 $retries); do
    if curl -sf "$url" > /dev/null 2>&1; then
      info "$name is ready"
      return 0
    fi
    echo -n "."
    sleep 3
  done
  error "$name failed to start. Run: docker compose logs $name"
}

echo ""
wait_for "ticket-service"       "http://localhost:8081/actuator/health"
wait_for "orchestrator-service" "http://localhost:8082/actuator/health"
wait_for "notification-service" "http://localhost:8083/actuator/health"
wait_for "ai-enrichment"        "http://localhost:8000/health"
wait_for "api-gateway"          "http://localhost:8080/actuator/health"

echo ""
info "All services are up!"
echo ""
echo "  API Gateway:          http://localhost:8080"
echo "  Ticket Service:       http://localhost:8081"
echo "  Orchestrator Service: http://localhost:8082"
echo "  AI Enrichment:        http://localhost:8000/docs  (Swagger UI)"
echo "  Grafana:              http://localhost:3000       (admin / admin)"
echo "  Prometheus:           http://localhost:9090"
echo ""
info "Run smoke tests: ./scripts/smoke-test.sh"
