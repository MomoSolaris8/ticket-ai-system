# Ticket AI System

A **production-style** event-driven microservices demo showcasing AI integration in an enterprise Java backend — designed to reflect real-world patterns found in Swiss banking, insurance, and infrastructure companies.

> This project demonstrates how to integrate LLM-based AI enrichment into a Java/Spring Boot Cloud system with proper production characteristics: async decoupling, failure recovery, idempotency, observability, and role-based access control.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT / UI                              │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTP + JWT
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│              api-gateway  (Spring Cloud Gateway)                │
│         JWT validation · Rate limiting · Routing                │
└──────────────┬──────────────────────┬───────────────────────────┘
               │                      │
               ▼                      ▼
    ┌──────────────────┐    ┌─────────────────────────────────┐
    │  ticket-service  │    │      orchestrator-service        │
    │                  │    │                                  │
    │  POST /tickets   │    │  Consumes: ticket.created        │
    │  GET  /tickets   │    │  → Calls ai-enrichment           │
    │                  │    │  → Writes result back            │
    │  State machine:  │    │  → Retry + DLQ on failure        │
    │  OPEN            │    │  → Idempotency via Redis         │
    │  → ENRICHED      │    └──────────────┬──────────────────┘
    │  → ASSIGNED      │                   │ HTTP
    │  → NEEDS_REVIEW  │                   ▼
    └────────┬─────────┘    ┌─────────────────────────────────┐
             │              │       ai-enrichment              │
             │              │       (Python / FastAPI)         │
             │              │                                  │
             │              │  POST /enrich                    │
             │              │  → Azure OpenAI (GPT-4o-mini)    │
             │              │  Returns: category, priority,    │
             │              │           summary, confidence    │
             │              └─────────────────────────────────┘
             │
             │  publish TicketCreated / TicketEnriched
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                           KAFKA                                  │
│  ticket.created  ·  ticket.enriched  ·  ticket.created.DLT      │
└─────────────────────────────────────────────────────────────────┘
             │ consume ticket.enriched
             ▼
    ┌──────────────────────┐
    │  notification-service│
    │  Webhook / Email     │
    └──────────────────────┘
```

---

## Production Characteristics

| Pattern | Implementation |
|---------|---------------|
| **Async decoupling** | Kafka event bus between all services |
| **Retry with backoff** | `@RetryableTopic` — 3 attempts, exponential (1s → 2s → 4s) |
| **Dead Letter Queue** | `ticket.created.DLT` → marks ticket `NEEDS_MANUAL_REVIEW` |
| **Idempotency** | Redis key per `ticketId` prevents duplicate AI calls |
| **State machine** | Ticket transitions: OPEN → ENRICHED → ASSIGNED |
| **Human fallback** | DLT handler triggers manual review — system never silently fails |
| **TraceId propagation** | Micrometer Tracing through all services |
| **Role-based access** | JWT roles: USER / AGENT / ADMIN / SERVICE |
| **Metrics** | Prometheus + Grafana dashboard (enrichment rate, error rate, DLT count) |

---

## Event Flow

### Happy Path
```
1. POST /api/tickets           → ticket-service creates ticket (status: OPEN)
2. TicketCreatedEvent          → published to Kafka topic: ticket.created
3. orchestrator-service        → consumes event, checks Redis idempotency key
4. ai-enrichment               → classifies + summarises ticket via Azure OpenAI
5. orchestrator-service        → PATCH /api/tickets/{id}/enrich
6. ticket-service              → updates status to ENRICHED
7. TicketEnrichedEvent         → published to Kafka topic: ticket.enriched
8. notification-service        → sends webhook/email to assignee
```

### Failure Path
```
ai-enrichment timeout/error
   → orchestrator retries 3x (exponential backoff)
   → after 3 failures: event sent to ticket.created.DLT
   → DLT handler: PATCH /api/tickets/{id}/manual-review
   → ticket status: NEEDS_MANUAL_REVIEW
   → human agent takes over — no data loss, no silent failure
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| API Gateway | Spring Cloud Gateway 2023 |
| Business Services | Spring Boot 3.3, Java 21 |
| Messaging | Apache Kafka (Confluent) |
| AI Service | Python 3.12, FastAPI, Azure OpenAI |
| Database | PostgreSQL 16 + Flyway migrations |
| Idempotency | Redis 7 |
| Auth | JWT (Keycloak-compatible) |
| Observability | Prometheus + Grafana + Micrometer Tracing |
| Packaging | Docker Compose |

---

## Running Locally

### Prerequisites
- Docker + Docker Compose
- Azure OpenAI API key (or use the Ollama fallback — see below)

### 1. Configure environment
```bash
cp .env.example .env
# Edit .env:
# AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
# AZURE_OPENAI_API_KEY=your-key
# AZURE_OPENAI_DEPLOYMENT=gpt-4o-mini
```

### 2. Start all services
```bash
docker compose up --build
```

### 3. Create a ticket
```bash
curl -X POST http://localhost:8080/api/tickets \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt>" \
  -d '{
    "title": "Cannot access my account after password reset",
    "description": "I reset my password yesterday but now I get an error 403 on login."
  }'
```

### 4. Check AI enrichment result
```bash
curl http://localhost:8080/api/tickets/{id} \
  -H "Authorization: Bearer <your-jwt>"

# Response includes:
# "status": "ENRICHED",
# "aiCategory": "ACCOUNT",
# "aiPriority": "HIGH",
# "aiSummary": "User is unable to log in after a password reset, receiving a 403 error."
```

### 5. View metrics
- Grafana: http://localhost:3000 (admin / admin)
- Prometheus: http://localhost:9090

### Offline / No Azure key? Use Ollama fallback
```bash
# In ai-enrichment/.env
OLLAMA_MODE=true
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_MODEL=llama3.2
```

---

## Key Design Decisions

### Why Python for AI enrichment?
The AI layer is intentionally decoupled into a Python microservice. AI tooling (model clients, prompt libraries) evolves faster than enterprise Java frameworks. Keeping it separate means the LLM can be swapped (OpenAI → Ollama → Claude) without touching Java business logic.

### Why Redis for idempotency instead of DB?
Kafka at-least-once delivery can cause duplicate events. A Redis key with TTL is faster than a DB unique constraint check and avoids write contention on the tickets table during retries.

### Why manual review fallback?
In regulated industries (FINMA, insurance compliance), AI must never silently fail. Every ticket must reach a human if automation fails. The DLT handler ensures auditability and human oversight — a requirement in Swiss financial services.

---

## Monitoring

Key metrics tracked:

| Metric | Description |
|--------|-------------|
| `ticket.enrichment.success` | Successful AI enrichments per minute |
| `ticket.enrichment.error` | Failed enrichment attempts |
| `ticket.enrichment.dlt` | Events that hit the Dead Letter Topic |
| `ticket.enrichment.duplicate` | Idempotency filter hits |
| `http.server.requests` | Latency per endpoint |

---

## Project Structure

```
ticket-ai-system/
├── api-gateway/              Spring Cloud Gateway + JWT
├── ticket-service/           Core CRUD + state machine + Kafka producer
├── orchestrator-service/     Kafka consumer + AI integration + retry/DLQ
├── notification-service/     Kafka consumer + webhook dispatch
├── ai-enrichment/            Python FastAPI + Azure OpenAI
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/
├── docker-compose.yml
└── README.md
```

---

## Relevance to Enterprise Use Cases

This architecture directly maps to real patterns used in Swiss financial institutions:

- **Insurance claims processing** — incoming claim documents → AI classification → routing to specialist
- **Bank KYC document review** — uploaded documents → AI extraction → compliance queue
- **Internal compliance search** — staff queries → RAG over policy documents → audited response log

The key principle: **AI assists, humans retain control**. The system is designed so that AI failure never blocks the business process.
