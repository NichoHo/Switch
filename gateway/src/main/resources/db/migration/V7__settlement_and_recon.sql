ALTER TABLE merchant ADD COLUMN rate_bps INTEGER NOT NULL DEFAULT 250;          -- 2.50% gateway fee
ALTER TABLE merchant ADD COLUMN fixed_fee_minor BIGINT NOT NULL DEFAULT 30;     -- per-transaction gateway fee

ALTER TABLE payment_operation ADD COLUMN settlement_state TEXT NOT NULL DEFAULT 'UNSETTLED'
    CHECK (settlement_state IN ('UNSETTLED', 'SETTLED'));

CREATE TABLE settlement_batch (
    id                 UUID PRIMARY KEY,
    merchant_id        UUID NOT NULL REFERENCES merchant(id),
    currency           VARCHAR(3) NOT NULL,
    acquirer_id        TEXT NOT NULL,
    business_date      DATE NOT NULL,
    gross_minor        BIGINT NOT NULL,
    refunds_minor      BIGINT NOT NULL,
    scheme_fee_minor   BIGINT NOT NULL,
    gateway_fee_minor  BIGINT NOT NULL,
    net_minor          BIGINT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT unique_settlement_batch UNIQUE (merchant_id, currency, acquirer_id, business_date)
);

CREATE TABLE settlement_item (                       -- insert-only
    id                     UUID PRIMARY KEY,
    batch_id               UUID NOT NULL REFERENCES settlement_batch(id),
    payment_operation_id   UUID NOT NULL REFERENCES payment_operation(id),
    payment_id             UUID NOT NULL REFERENCES payment(id),
    amount_minor           BIGINT NOT NULL,
    currency               VARCHAR(3) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON settlement_item (batch_id);

CREATE TABLE recon_exception (                        -- mutable: resolution fields only
    id                     UUID PRIMARY KEY,
    business_date          DATE NOT NULL,
    acquirer_id            TEXT NOT NULL,
    type                   TEXT NOT NULL CHECK (type IN (
                               'MISSING_AT_ACQUIRER', 'UNKNOWN_AT_GATEWAY', 'AMOUNT_MISMATCH',
                               'DUPLICATE_AT_ACQUIRER', 'CURRENCY_MISMATCH', 'DATE_OUT_OF_WINDOW')),
    severity               TEXT NOT NULL,
    reference              TEXT,
    internal_amount_minor  BIGINT,
    file_amount_minor      BIGINT,
    internal_currency      VARCHAR(3),
    file_currency          VARCHAR(3),
    detail                 TEXT,
    resolved_at            TIMESTAMPTZ,
    resolved_by            TEXT,
    resolution_note        TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON recon_exception (business_date, acquirer_id);
CREATE INDEX ON recon_exception (type) WHERE resolved_at IS NULL;

CREATE TABLE dispute (
    id               UUID PRIMARY KEY,
    payment_id       UUID NOT NULL REFERENCES payment(id),
    state            TEXT NOT NULL,
    reason_code      TEXT,
    amount_minor     BIGINT NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    opened_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    evidence_due_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON dispute (payment_id);
