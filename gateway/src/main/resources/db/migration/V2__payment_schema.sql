CREATE TABLE payment (
    id                     UUID PRIMARY KEY,
    merchant_id            UUID NOT NULL REFERENCES merchant(id),
    merchant_reference     TEXT NOT NULL,
    card_token             TEXT NOT NULL REFERENCES card_token(token),
    currency               VARCHAR(3) NOT NULL,
    amount_minor           BIGINT NOT NULL CHECK (amount_minor > 0),
    captured_amount_minor  BIGINT NOT NULL DEFAULT 0 CHECK (captured_amount_minor >= 0),
    refunded_amount_minor  BIGINT NOT NULL DEFAULT 0 CHECK (refunded_amount_minor >= 0),
    state                  TEXT NOT NULL,
    risk_decision          TEXT,
    risk_score             INTEGER,
    acquirer_id            TEXT,
    acquirer_reference     TEXT,
    auth_code              TEXT,
    expires_at             TIMESTAMPTZ,
    dispute_state          TEXT,
    version                BIGINT NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT capture_within_auth  CHECK (captured_amount_minor <= amount_minor),
    CONSTRAINT refund_within_capture CHECK (refunded_amount_minor <= captured_amount_minor),
    CONSTRAINT unique_merchant_ref  UNIQUE (merchant_id, merchant_reference)
);

CREATE TABLE payment_operation (
    id            UUID PRIMARY KEY,
    payment_id    UUID NOT NULL REFERENCES payment(id),
    type          TEXT NOT NULL,
    amount_minor  BIGINT,
    currency      VARCHAR(3),
    state         TEXT NOT NULL,
    acquirer_id   TEXT,
    acquirer_ref  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_event (
    id           BIGSERIAL PRIMARY KEY,
    payment_id   UUID NOT NULL REFERENCES payment(id),
    seq          INTEGER NOT NULL,
    from_state   TEXT,
    to_state     TEXT NOT NULL,
    operation_id UUID,
    actor        TEXT NOT NULL,
    reason_code  TEXT,
    payload      JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (payment_id, seq)
);
