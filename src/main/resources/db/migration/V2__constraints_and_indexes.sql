-- V2: data-integrity constraints and indexes for the real query patterns.

-- Safety net against overselling: even if application code has a bug, stock can never go negative.
ALTER TABLE products ADD CONSTRAINT chk_products_stock CHECK (stock >= 0);
ALTER TABLE products ADD CONSTRAINT chk_products_price CHECK (price >= 0);
ALTER TABLE products ADD CONSTRAINT chk_products_type
    CHECK (type IN ('PLATE', 'KEYCHAIN', 'STICKER', 'NFC_TAG', 'QR_TAG'));

ALTER TABLE orders ADD CONSTRAINT chk_orders_total CHECK (total >= 0);
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING', 'PAID', 'IN_PRODUCTION', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

ALTER TABLE order_items ADD CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0);

ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'));
-- The application normalizes emails to lower case; the database enforces it.
ALTER TABLE users ADD CONSTRAINT chk_users_email_lower CHECK (email = lower(email));

ALTER TABLE digital_experiences ADD CONSTRAINT chk_experiences_type
    CHECK (type IN ('GOOGLE_REVIEW', 'DIGITAL_MENU', 'PROFILE', 'EVENT_BADGE', 'PET_ID', 'DIGITAL_GIFT', 'CUSTOM'));

-- Catalog: GET /api/products filters by active and optionally by type.
CREATE INDEX idx_products_active_type ON products(active, type);

-- "My orders" is filtered by user and sorted by newest first (replaces idx_orders_user).
DROP INDEX idx_orders_user;
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- PostgreSQL does not index foreign keys automatically.
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);
CREATE INDEX idx_experiences_order_item ON digital_experiences(order_item_id);

-- Links are always read ordered by position (replaces idx_links_experience).
DROP INDEX idx_links_experience;
CREATE INDEX idx_links_experience_position ON experience_links(experience_id, position);
