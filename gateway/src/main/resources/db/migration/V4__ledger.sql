CREATE TABLE ledger_account (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    normal_balance TEXT NOT NULL CHECK (normal_balance IN ('DEBIT', 'CREDIT'))
);

INSERT INTO ledger_account (id, name, normal_balance) VALUES
('11111111-1111-1111-1111-111111111111', 'ACQUIRER_CLEARING', 'DEBIT'),
('22222222-2222-2222-2222-222222222222', 'MERCHANT_RECEIVABLE', 'DEBIT'),
('33333333-3333-3333-3333-333333333333', 'MERCHANT_PAYABLE', 'CREDIT'),
('44444444-4444-4444-4444-444444444444', 'GATEWAY_REVENUE', 'CREDIT'),
('55555555-5555-5555-5555-555555555555', 'SCHEME_FEES', 'DEBIT'),
('66666666-6666-6666-6666-666666666666', 'REFUNDS_CLEARING', 'DEBIT'),
('77777777-7777-7777-7777-777777777777', 'CHARGEBACK_RESERVE', 'CREDIT');

CREATE TABLE ledger_entry (
    id BIGSERIAL PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES ledger_account(id),
    direction TEXT NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    payment_id UUID,
    memo TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON ledger_entry (transaction_id);

-- Append only triggers
CREATE RULE prevent_ledger_update AS ON UPDATE TO ledger_entry DO INSTEAD NOTHING;
CREATE RULE prevent_ledger_delete AS ON DELETE TO ledger_entry DO INSTEAD NOTHING;

-- Deferred constraint trigger
CREATE OR REPLACE FUNCTION assert_ledger_balanced() RETURNS TRIGGER AS $$
DECLARE imbalance RECORD;
BEGIN
    SELECT currency,
           SUM(CASE WHEN direction='DEBIT' THEN amount_minor ELSE -amount_minor END) AS delta
      INTO imbalance
      FROM ledger_entry WHERE transaction_id = NEW.transaction_id
     GROUP BY currency HAVING SUM(CASE WHEN direction='DEBIT'
                                       THEN amount_minor ELSE -amount_minor END) <> 0
     LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'ledger transaction % unbalanced in %: %',
              NEW.transaction_id, imbalance.currency, imbalance.delta;
    END IF;
    RETURN NULL;
END; $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_balanced
    AFTER INSERT ON ledger_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_ledger_balanced();
