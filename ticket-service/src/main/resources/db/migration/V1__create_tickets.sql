CREATE TABLE tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    ai_category     VARCHAR(100),
    ai_priority     VARCHAR(50),
    ai_summary      TEXT,
    assigned_to     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_created_at ON tickets(created_at DESC);
