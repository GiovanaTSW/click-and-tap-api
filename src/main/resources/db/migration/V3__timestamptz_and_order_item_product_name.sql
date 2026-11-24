-- V3: timezone-aware timestamps and a product name snapshot on order items.

-- 1) created_at becomes TIMESTAMPTZ so the API can return unambiguous UTC instants (Flutter parses them
--    correctly in any timezone). Existing values were written by a server running in Mexico City time.
ALTER TABLE users ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE users ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'America/Mexico_City';
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE orders ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'America/Mexico_City';
ALTER TABLE orders ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE digital_experiences ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE digital_experiences ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'America/Mexico_City';
ALTER TABLE digital_experiences ALTER COLUMN created_at SET DEFAULT NOW();

-- 2) Order items keep the product name as it was when the order was placed. Products can now be
--    soft-deleted or renamed, and old orders must still show what the customer bought.
ALTER TABLE order_items ADD COLUMN product_name VARCHAR(150);
UPDATE order_items oi SET product_name = p.name FROM products p WHERE p.id = oi.product_id;
ALTER TABLE order_items ALTER COLUMN product_name SET NOT NULL;
