CREATE TABLE idempotency_record (
    merchant_id          UUID NOT NULL,
    idempotency_key      TEXT NOT NULL,
    request_fingerprint  BYTEA NOT NULL,
    state                TEXT NOT NULL,
    response_status      INTEGER,
    response_body        JSONB,
    payment_id           UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (merchant_id, idempotency_key)
);
