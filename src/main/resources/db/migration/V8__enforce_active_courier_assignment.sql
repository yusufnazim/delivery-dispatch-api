ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_delivery_orders_active_courier
    ON delivery_orders (courier_id)
    WHERE status IN ('ASSIGNED', 'PICKED_UP');
