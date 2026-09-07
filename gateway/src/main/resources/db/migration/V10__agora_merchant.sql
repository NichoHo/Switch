-- The inbound API contract for Agora's pay service (AGORA_SPEC.md section 9.1):
-- a merchant account with a real, working api_key_hash. The /admin/seed
-- endpoint's demo merchant stores the literal string "hashed-key" as its
-- hash, which is not a real SHA-256 digest of anything and so cannot
-- authenticate a caller; this migration adds one that actually works.
--
-- Raw key: "agora-service-integration-key" (dev/demo secret, same tier as
-- this repo's other dev-insecure-* defaults). SHA-256 hex digest below,
-- matching com.switchpay.merchant.ApiKeyHasher.
INSERT INTO merchant (id, name, api_key_hash, webhook_secret, deny_threshold, challenge_threshold)
VALUES (
    '11111111-1111-4111-8111-111111111111',
    'Agora Marketplace',
    'f0abb8aeda87a10f8adcea9b7854264c189d226acdc126f0303a6a0d19c3ba46',
    'agora-dev-webhook-secret',
    80,
    60
)
ON CONFLICT (id) DO NOTHING;
