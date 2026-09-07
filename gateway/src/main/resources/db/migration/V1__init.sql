CREATE TABLE merchant (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    api_key_hash TEXT NOT NULL,
    webhook_secret TEXT NOT NULL,
    deny_threshold INTEGER NOT NULL DEFAULT 80,
    challenge_threshold INTEGER NOT NULL DEFAULT 60,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE card_token (
    token             TEXT PRIMARY KEY,              -- 'tok_' || 32 hex
    merchant_id       UUID NOT NULL REFERENCES merchant(id),
    pan_ciphertext    BYTEA NOT NULL,                -- AES-256-GCM, IV prefixed
    key_version       SMALLINT NOT NULL,             -- rotation story
    pan_fingerprint   BYTEA NOT NULL,                -- HMAC-SHA256(pan, pepper)
    bin               VARCHAR(8) NOT NULL,
    last4             VARCHAR(4) NOT NULL,
    brand             TEXT NOT NULL,                 -- VISA | MASTERCARD | AMEX
    funding_type      TEXT NOT NULL,                 -- CREDIT | DEBIT | PREPAID
    issuer_country    VARCHAR(2) NOT NULL,
    exp_month         SMALLINT NOT NULL CHECK (exp_month BETWEEN 1 AND 12),
    exp_year          SMALLINT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ON card_token (merchant_id, pan_fingerprint);
