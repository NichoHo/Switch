CREATE TABLE risk_assessment (              -- insert-only (§5.2)
    id                     UUID PRIMARY KEY,
    payment_id             UUID NOT NULL REFERENCES payment(id),
    merchant_id            UUID NOT NULL REFERENCES merchant(id),
    pan_fingerprint        TEXT NOT NULL,    -- hex(card_token.pan_fingerprint), never the PAN itself
    ip_address             TEXT,
    email_hash             TEXT,
    device_fingerprint     TEXT,
    amount_minor           BIGINT NOT NULL,
    score                  INTEGER NOT NULL,
    decision               TEXT NOT NULL,
    deny_threshold         INTEGER NOT NULL,
    challenge_threshold    INTEGER NOT NULL,
    ruleset_version        TEXT NOT NULL,
    breakdown              JSONB NOT NULL,   -- one entry per rule: {code, score, reason}
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Velocity rules query "how many of these in the last N hours" — one covering index per signal.
CREATE INDEX ON risk_assessment (pan_fingerprint, created_at);
CREATE INDEX ON risk_assessment (ip_address, created_at) WHERE ip_address IS NOT NULL;
CREATE INDEX ON risk_assessment (email_hash, created_at) WHERE email_hash IS NOT NULL;
CREATE INDEX ON risk_assessment (device_fingerprint) WHERE device_fingerprint IS NOT NULL;
