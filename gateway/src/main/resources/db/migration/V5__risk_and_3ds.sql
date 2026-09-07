ALTER TABLE payment ADD COLUMN liability_shift BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE threeds_challenge (
    challenge_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
