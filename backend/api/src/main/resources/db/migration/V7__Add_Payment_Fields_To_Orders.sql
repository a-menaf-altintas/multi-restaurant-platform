-- File: V7__Add_Payment_Fields_To_Orders.sql

ALTER TABLE orders ADD COLUMN payment_intent_id VARCHAR(255);
ALTER TABLE orders ADD COLUMN payment_status_detail VARCHAR(255);