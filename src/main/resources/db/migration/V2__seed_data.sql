-- =============================================================================
-- V2__seed_data.sql — Catalog seed data for ecommerce-api (PostgreSQL 16)
--
-- Scope: catalog only — categories, products, and one inventory row per product.
-- NOT seeded here: users/admin (created via CommandLineRunner in Phase 2),
-- orders, order_items, payments, product_images.
--
-- Why business-key subqueries instead of hardcoded ids:
--   categories and products use BIGSERIAL, so their ids are assigned by the DB
--   at INSERT time and are NOT known in advance. Hardcoding id = 1, 2, 3 would
--   break the moment the sequence starts elsewhere or rows are inserted in a
--   different order. Instead we resolve foreign keys by their stable business
--   key — categories.name / products.name — via subquery. This requires every
--   product name to be UNIQUE across the seed, otherwise the inventory subquery
--   would return multiple rows and fail.
--
-- DB-managed columns are intentionally omitted:
--   id (BIGSERIAL auto), created_at/updated_at (DEFAULT now()),
--   version (DEFAULT 0), products.is_active (DEFAULT true).
--
-- Plain INSERTs (no ON CONFLICT): Flyway guarantees this migration runs exactly
-- once per database, so idempotency guards are unnecessary.
--
-- Insert order follows FK dependency: categories -> products -> inventory.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1) Categories
-- -----------------------------------------------------------------------------
INSERT INTO categories (name, description) VALUES
    ('Electronics', 'Phones, laptops, audio and other consumer electronics.'),
    ('Books',       'Programming, technology and general reading.'),
    ('Clothing',    'Apparel, footwear and accessories.');


-- -----------------------------------------------------------------------------
-- 2) Products — category_id resolved by business key (category name).
--    Prices are NUMERIC(19,4) and all >= 0 (respects ck_products_price_non_negative).
--    Every product name is unique across the whole seed.
-- -----------------------------------------------------------------------------

-- Electronics
INSERT INTO products (name, description, price, category_id) VALUES
    ('Apple iPhone 15 Pro',
     '6.1-inch Super Retina XDR display, A17 Pro chip, 256GB.',
     999.9900,
     (SELECT id FROM categories WHERE name = 'Electronics')),
    ('Samsung Galaxy S24 Ultra',
     '6.8-inch Dynamic AMOLED, Snapdragon 8 Gen 3, 512GB.',
     1199.0000,
     (SELECT id FROM categories WHERE name = 'Electronics')),
    ('Sony WH-1000XM5 Headphones',
     'Wireless noise-cancelling over-ear headphones.',
     349.9900,
     (SELECT id FROM categories WHERE name = 'Electronics')),
    ('Dell XPS 13 Laptop',
     '13.4-inch FHD+, Intel Core Ultra 7, 16GB RAM, 512GB SSD.',
     1299.5000,
     (SELECT id FROM categories WHERE name = 'Electronics'));

-- Books
INSERT INTO products (name, description, price, category_id) VALUES
    ('Clean Code by Robert C. Martin',
     'A Handbook of Agile Software Craftsmanship.',
     39.9900,
     (SELECT id FROM categories WHERE name = 'Books')),
    ('The Pragmatic Programmer',
     'Your Journey to Mastery, 20th Anniversary Edition.',
     49.9900,
     (SELECT id FROM categories WHERE name = 'Books')),
    ('Designing Data-Intensive Applications',
     'The Big Ideas Behind Reliable, Scalable, and Maintainable Systems.',
     59.9900,
     (SELECT id FROM categories WHERE name = 'Books'));

-- Clothing
INSERT INTO products (name, description, price, category_id) VALUES
    ('Levi''s 501 Original Jeans',
     'Classic straight-fit denim jeans.',
     69.9900,
     (SELECT id FROM categories WHERE name = 'Clothing')),
    ('Nike Dri-FIT Training T-Shirt',
     'Breathable moisture-wicking short-sleeve tee.',
     29.9900,
     (SELECT id FROM categories WHERE name = 'Clothing')),
    ('Adidas Essentials Fleece Hoodie',
     'Regular-fit cotton-blend pullover hoodie.',
     89.9900,
     (SELECT id FROM categories WHERE name = 'Clothing'));


-- -----------------------------------------------------------------------------
-- 3) Inventory — exactly one row per product (1:1 via uq_inventory_product_id).
--    product_id resolved by business key (product name). reserved_quantity = 0,
--    so ck_inventory_quantities (reserved <= stock, both >= 0) always holds.
--    version is left to DEFAULT 0.
-- -----------------------------------------------------------------------------
INSERT INTO inventory (product_id, stock_quantity, reserved_quantity) VALUES
    ((SELECT id FROM products WHERE name = 'Apple iPhone 15 Pro'),                   75, 0),
    ((SELECT id FROM products WHERE name = 'Samsung Galaxy S24 Ultra'),              60, 0),
    ((SELECT id FROM products WHERE name = 'Sony WH-1000XM5 Headphones'),            90, 0),
    ((SELECT id FROM products WHERE name = 'Dell XPS 13 Laptop'),                    50, 0),
    ((SELECT id FROM products WHERE name = 'Clean Code by Robert C. Martin'),       100, 0),
    ((SELECT id FROM products WHERE name = 'The Pragmatic Programmer'),              85, 0),
    ((SELECT id FROM products WHERE name = 'Designing Data-Intensive Applications'), 70, 0),
    ((SELECT id FROM products WHERE name = 'Levi''s 501 Original Jeans'),            65, 0),
    ((SELECT id FROM products WHERE name = 'Nike Dri-FIT Training T-Shirt'),         95, 0),
    ((SELECT id FROM products WHERE name = 'Adidas Essentials Fleece Hoodie'),       80, 0);
