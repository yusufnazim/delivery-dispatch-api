CREATE TABLE delivery_events (
    id BIGSERIAL PRIMARY KEY,
    delivery_order_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_delivery_events_delivery_order FOREIGN KEY (delivery_order_id) REFERENCES delivery_orders (id),
    CONSTRAINT chk_delivery_events_event_type CHECK (
        event_type IN (
            'ORDER_CREATED',
            'COURIER_ASSIGNED',
            'ORDER_PICKED_UP',
            'ORDER_DELIVERED',
            'ORDER_CANCELLED'
        )
    )
);

CREATE INDEX idx_delivery_events_delivery_order_id ON delivery_events (delivery_order_id);
CREATE INDEX idx_delivery_events_created_at ON delivery_events (created_at);
