ALTER TABLE merchant ADD COLUMN webhook_url TEXT;

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    merchant_id    UUID NOT NULL REFERENCES merchant(id),
    event_type     TEXT NOT NULL,
    payload        JSONB NOT NULL,
    state          TEXT NOT NULL DEFAULT 'PENDING',
    attempts       INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON outbox_event (state, next_attempt_at) WHERE state = 'PENDING';
