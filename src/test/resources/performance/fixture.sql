-- Only loaded by BackendPerformanceCheck into its fresh Testcontainers database.
INSERT INTO users (id, email, password_hash, role, created_at, updated_at)
SELECT n, 'customer-' || n || '@perf.example', 'replaced-before-login', 'CUSTOMER',
       TIMESTAMPTZ '2025-01-01 00:00:00Z', TIMESTAMPTZ '2025-01-01 00:00:00Z'
FROM generate_series(1, 20) AS n;

INSERT INTO users (id, email, password_hash, role, created_at, updated_at)
VALUES (100, 'dispatcher@perf.example', 'replaced-before-login', 'DISPATCHER',
        TIMESTAMPTZ '2025-01-01 00:00:00Z', TIMESTAMPTZ '2025-01-01 00:00:00Z');

INSERT INTO users (id, email, password_hash, role, courier_display_name,
                   courier_availability_status, courier_latitude, courier_longitude,
                   created_at, updated_at)
SELECT 1000 + n, 'courier-' || n || '@perf.example', 'replaced-before-login', 'COURIER',
       'Courier ' || n, 'AVAILABLE', 41.0 + n * 0.0001, 29.0,
       TIMESTAMPTZ '2025-01-01 00:00:00Z', TIMESTAMPTZ '2025-01-01 00:00:00Z'
FROM generate_series(1, 500) AS n;

INSERT INTO delivery_orders (id, customer_id, courier_id, status,
    pickup_address, pickup_latitude, pickup_longitude,
    dropoff_address, dropoff_latitude, dropoff_longitude, created_at, updated_at)
SELECT n, (n - 1) / 500 + 1,
       CASE WHEN n % 2 = 0 THEN 1001 + ((n / 2 - 1) % 500) END,
       CASE WHEN n % 2 = 0 THEN 'DELIVERED' ELSE 'PENDING' END,
       'Performance pickup', 41.0, 29.0, 'Performance dropoff', 41.01, 29.01,
       TIMESTAMPTZ '2025-01-01 00:00:00Z' + n * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-01 00:00:00Z' + (n + 3) * INTERVAL '1 second'
FROM generate_series(1, 10000) AS n;

INSERT INTO delivery_events (delivery_order_id, event_type, description, created_at)
SELECT id, 'ORDER_CREATED', 'Performance fixture', created_at FROM delivery_orders;

INSERT INTO delivery_events (delivery_order_id, event_type, description, created_at)
SELECT o.id, e.type, 'Performance fixture', o.created_at + e.seconds * INTERVAL '1 second'
FROM delivery_orders o
CROSS JOIN (VALUES ('COURIER_ASSIGNED', 1), ('ORDER_PICKED_UP', 2), ('ORDER_DELIVERED', 3))
    AS e(type, seconds)
WHERE o.status = 'DELIVERED';

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT max(id) FROM users));
SELECT setval(pg_get_serial_sequence('delivery_orders', 'id'), (SELECT max(id) FROM delivery_orders));

ANALYZE users;
ANALYZE delivery_orders;
ANALYZE delivery_events;
