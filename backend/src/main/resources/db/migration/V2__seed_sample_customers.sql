-- Sample data exercised by the ledger-mode security tests and manual API checks.
-- "subject" values below are stand-ins for a real identity provider's user id.
INSERT INTO customer (subject, status, customer_role) VALUES
    ('auth0|sample-brand-a-role1', 'active', 'role1'),
    ('auth0|sample-brand-b-role2', 'active', 'role2'),
    ('auth0|sample-both-brands-role2', 'active', 'role2'),
    ('auth0|sample-suspended-brand-a-role2', 'suspended', 'role2');

INSERT INTO customer_brand (customer_id, brand_id)
SELECT id, 'brand-a' FROM customer WHERE subject = 'auth0|sample-brand-a-role1';

INSERT INTO customer_brand (customer_id, brand_id)
SELECT id, 'brand-b' FROM customer WHERE subject = 'auth0|sample-brand-b-role2';

INSERT INTO customer_brand (customer_id, brand_id)
SELECT id, 'brand-a' FROM customer WHERE subject = 'auth0|sample-both-brands-role2';

INSERT INTO customer_brand (customer_id, brand_id)
SELECT id, 'brand-b' FROM customer WHERE subject = 'auth0|sample-both-brands-role2';

INSERT INTO customer_brand (customer_id, brand_id)
SELECT id, 'brand-a' FROM customer WHERE subject = 'auth0|sample-suspended-brand-a-role2';
