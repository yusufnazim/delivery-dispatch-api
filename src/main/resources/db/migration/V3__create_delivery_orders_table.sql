CREATE TABLE delivery_orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    courier_id BIGINT,
    status VARCHAR(32) NOT NULL,
    pickup_address VARCHAR(500) NOT NULL,
    pickup_latitude NUMERIC(9, 6) NOT NULL,
    pickup_longitude NUMERIC(9, 6) NOT NULL,
    dropoff_address VARCHAR(500) NOT NULL,
    dropoff_latitude NUMERIC(9, 6) NOT NULL,
    dropoff_longitude NUMERIC(9, 6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_delivery_orders_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_delivery_orders_courier FOREIGN KEY (courier_id) REFERENCES users (id),
    CONSTRAINT chk_delivery_orders_status CHECK (
        status IN ('PENDING', 'ASSIGNED', 'PICKED_UP', 'DELIVERED', 'CANCELLED')
    ),
    CONSTRAINT chk_delivery_orders_pickup_latitude CHECK (
        pickup_latitude BETWEEN -90 AND 90
    ),
    CONSTRAINT chk_delivery_orders_pickup_longitude CHECK (
        pickup_longitude BETWEEN -180 AND 180
    ),
    CONSTRAINT chk_delivery_orders_dropoff_latitude CHECK (
        dropoff_latitude BETWEEN -90 AND 90
    ),
    CONSTRAINT chk_delivery_orders_dropoff_longitude CHECK (
        dropoff_longitude BETWEEN -180 AND 180
    )
);

CREATE INDEX idx_delivery_orders_customer_id ON delivery_orders (customer_id);
CREATE INDEX idx_delivery_orders_courier_id ON delivery_orders (courier_id);
CREATE INDEX idx_delivery_orders_status ON delivery_orders (status);
