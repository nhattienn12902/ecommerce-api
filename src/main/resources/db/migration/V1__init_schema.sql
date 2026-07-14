-- =============================================================================
-- V1__init_schema.sql — Initial schema for ecommerce-api (PostgreSQL 16)
--
-- Order of statements (dependency-driven):
--   1) ENUM types
--   2) set_updated_at() function
--   3) Tables
--   4) Indexes
--   5) BEFORE UPDATE triggers
--
-- PK strategy:
--   UUID     -> users, refresh_tokens, orders  (exposed in URLs, non-guessable)
--   BIGSERIAL-> categories, products, product_images, inventory,
--               order_items, payments          (internal catalog/order rows)
--
-- Monetary values : NUMERIC(19,4)  (never float/double)
-- Timestamps      : TIMESTAMPTZ    (never timestamp without time zone)
-- gen_random_uuid(): available in PostgreSQL 16 core, no pgcrypto extension needed.
-- =============================================================================


-- =============================================================================
-- 1) ENUM TYPES (native PostgreSQL enums; labels match Java enums)
-- =============================================================================
CREATE TYPE user_role      AS ENUM ('USER', 'ADMIN');
CREATE TYPE order_status   AS ENUM ('PENDING', 'PAID', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED');
CREATE TYPE payment_method AS ENUM ('VNPAY', 'MOMO', 'PAYPAL', 'COD');
CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED');


-- =============================================================================
-- 2) SHARED FUNCTION — keep updated_at authoritative at the DB layer.
--    Attached to every table via a BEFORE UPDATE trigger below, so the
--    database (not the application) is the source of truth for updated_at.
-- =============================================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- 3) TABLES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- users — PK UUID (exposed in URLs, non-guessable)
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,   -- BCrypt hash, never plaintext
    full_name     VARCHAR(150) NOT NULL,
    role          user_role    NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------------
-- refresh_tokens — PK UUID
-- Stores only the SHA-256 HASH of the token (64 hex chars), never the raw token.
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,   -- SHA-256 hex digest of the raw token
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT false,
    ip_address  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- categories — PK BIGSERIAL (internal catalog)
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGSERIAL    NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- products — PK BIGSERIAL
-- is_active supports SOFT-DELETE: the DELETE product endpoint flips is_active
-- to false at the Service layer instead of hard-deleting the row. This keeps
-- order_items FKs valid and preserves the historical snapshot (see order_items).
-- Stock lives in the separate `inventory` table, not here.
-- ---------------------------------------------------------------------------
CREATE TABLE products (
    id          BIGSERIAL      NOT NULL,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       NUMERIC(19, 4) NOT NULL,
    category_id BIGINT         NOT NULL,
    is_active   BOOLEAN        NOT NULL DEFAULT true,   -- soft-delete flag
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT ck_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- ---------------------------------------------------------------------------
-- product_images — PK BIGSERIAL
-- ---------------------------------------------------------------------------
CREATE TABLE product_images (
    id         BIGSERIAL    NOT NULL,
    product_id BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,   -- S3 object key / URL
    is_primary BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_product_images PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- inventory — PK BIGSERIAL, 1:1 with products (enforced by uq_inventory_product_id)
-- Split out from `products` on purpose: stock has a different lifecycle and
-- much higher write frequency than catalog data. The `version` column backs
-- JPA @Version (optimistic locking) to prevent overselling under concurrent
-- checkout. reserved_quantity tracks stock held for in-flight orders.
-- ---------------------------------------------------------------------------
CREATE TABLE inventory (
    id                BIGSERIAL   NOT NULL,
    product_id        BIGINT      NOT NULL,
    stock_quantity    INTEGER     NOT NULL DEFAULT 0,
    reserved_quantity INTEGER     NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0,   -- JPA @Version, optimistic lock
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_inventory PRIMARY KEY (id),
    CONSTRAINT uq_inventory_product_id UNIQUE (product_id),
    CONSTRAINT ck_inventory_quantities
        CHECK (stock_quantity >= 0
           AND reserved_quantity >= 0
           AND reserved_quantity <= stock_quantity),
    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- orders — PK UUID (exposed in URLs, non-guessable)
-- ---------------------------------------------------------------------------
CREATE TABLE orders (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    user_id          UUID           NOT NULL,
    status           order_status   NOT NULL DEFAULT 'PENDING',
    total_amount     NUMERIC(19, 4) NOT NULL,
    shipping_address VARCHAR(500)   NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT ck_orders_total_non_negative CHECK (total_amount >= 0),
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- order_items — PK BIGSERIAL
-- SNAPSHOT PATTERN: product_name and unit_price are COPIED at purchase time,
-- not read live from products. This freezes the invoice so later product
-- price/name changes (or a soft-deleted product) never mutate historical
-- orders. product_id FK is kept with RESTRICT: products are soft-deleted, so
-- an order line always keeps a valid reference and the hard-delete path that
-- would orphan snapshots is blocked at the DB level.
-- ---------------------------------------------------------------------------
CREATE TABLE order_items (
    id           BIGSERIAL      NOT NULL,
    order_id     UUID           NOT NULL,
    product_id   BIGINT         NOT NULL,
    product_name VARCHAR(255)   NOT NULL,   -- snapshot of products.name at purchase
    unit_price   NUMERIC(19, 4) NOT NULL,   -- snapshot of products.price at purchase
    quantity     INTEGER        NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------------
-- payments — PK BIGSERIAL. Relationship order:payment is 1:N (retries allowed).
-- gateway_response stores the raw provider payload as JSONB for auditing.
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id               BIGSERIAL      NOT NULL,
    order_id         UUID           NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    method           payment_method NOT NULL,
    status           payment_status NOT NULL DEFAULT 'PENDING',
    transaction_id   VARCHAR(255),          -- gateway transaction reference
    gateway_response JSONB,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
);


-- =============================================================================
-- 4) INDEXES
--    PostgreSQL does NOT auto-index FK columns, so every FK gets an index.
--    UNIQUE constraints already create their own index (users.email,
--    refresh_tokens.token_hash, inventory.product_id, categories.name).
-- =============================================================================
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_products_category_id   ON products (category_id);
CREATE INDEX idx_product_images_product_id ON product_images (product_id);
CREATE INDEX idx_orders_user_id         ON orders (user_id);
CREATE INDEX idx_orders_status          ON orders (status);
CREATE INDEX idx_order_items_order_id   ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);
CREATE INDEX idx_payments_order_id      ON payments (order_id);

-- PARTIAL UNIQUE INDEXES (PostgreSQL-specific):
-- Allow at most one primary image per product, while permitting many
-- non-primary images (the uniqueness only applies WHERE is_primary = true).
CREATE UNIQUE INDEX uq_product_images_one_primary
    ON product_images (product_id) WHERE is_primary = true;

-- Enforce transaction_id uniqueness only for real (non-NULL) gateway refs,
-- so multiple pending payments with no transaction_id yet remain allowed.
CREATE UNIQUE INDEX uq_payments_transaction_id
    ON payments (transaction_id) WHERE transaction_id IS NOT NULL;


-- =============================================================================
-- 5) TRIGGERS — one BEFORE UPDATE trigger per table, all sharing set_updated_at()
-- =============================================================================
CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_refresh_tokens_set_updated_at
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_categories_set_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_products_set_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_product_images_set_updated_at
    BEFORE UPDATE ON product_images
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_inventory_set_updated_at
    BEFORE UPDATE ON inventory
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_orders_set_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_order_items_set_updated_at
    BEFORE UPDATE ON order_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_payments_set_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
