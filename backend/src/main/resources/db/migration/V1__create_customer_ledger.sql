-- Customer ledger: the record LedgerAuthorityResolver treats as authoritative instead of
-- token claims. "subject" is the identity provider's stable user id (the token's "sub"
-- claim) -- customers and identity-provider users are linked by that id, never by email.
CREATE TABLE customer (
    id BIGSERIAL PRIMARY KEY,
    subject VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('active', 'suspended', 'withdrawn')),
    customer_role VARCHAR(20) CHECK (customer_role IN ('role1', 'role2')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per brand a customer belongs to. brand_id values are free-form strings that the
-- application further validates against app.auth.known-brands before trusting them.
CREATE TABLE customer_brand (
    customer_id BIGINT NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    brand_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (customer_id, brand_id)
);
