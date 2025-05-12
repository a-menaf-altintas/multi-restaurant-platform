-- File: backend/api/src/main/resources/db/migration/V5__Add_OutForDeliveryAt_To_Orders.sql

-- Add the out_for_delivery_at column to the orders table
-- This column will store the timestamp when an order is marked as out for delivery.
ALTER TABLE orders
ADD COLUMN out_for_delivery_at TIMESTAMP;

-- Optional: Add an index if you anticipate querying by this timestamp frequently,
-- though it's less common for status timestamps compared to foreign keys or main status.
-- CREATE INDEX idx_orders_out_for_delivery_at ON orders (out_for_delivery_at);

