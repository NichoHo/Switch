CREATE TABLE risk_ruleset (
    version TEXT PRIMARY KEY,
    rules JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ensure only one ruleset can be active at a time
CREATE UNIQUE INDEX risk_ruleset_active_idx ON risk_ruleset (is_active) WHERE is_active = true;

-- Insert default ruleset 'v1' to maintain backwards compatibility
INSERT INTO risk_ruleset (version, rules, is_active)
VALUES ('v1', '{"AMOUNT_ANOMALY": "ACTIVE", "BIN_COUNTRY_MISMATCH": "ACTIVE", "BLOCKLIST_CARD": "ACTIVE", "BLOCKLIST_IP": "ACTIVE", "CARD_TESTING_PATTERN": "ACTIVE", "HIGH_RISK_COUNTRY": "ACTIVE", "NEW_DEVICE_HIGH_AMOUNT": "ACTIVE", "VELOCITY_CARD_1H": "ACTIVE", "VELOCITY_EMAIL_24H": "ACTIVE", "VELOCITY_IP_24H": "ACTIVE"}', true);

-- Add fields to risk_assessment to allow exact backtesting against historical contexts
ALTER TABLE risk_assessment 
ADD COLUMN bin_country TEXT,
ADD COLUMN ip_country TEXT,
ADD COLUMN is_new_device BOOLEAN DEFAULT false;
