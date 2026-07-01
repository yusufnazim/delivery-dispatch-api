ALTER TABLE users
    ADD COLUMN courier_display_name VARCHAR(120),
    ADD COLUMN courier_phone_number VARCHAR(32),
    ADD COLUMN courier_vehicle_type VARCHAR(32),
    ADD CONSTRAINT chk_users_courier_vehicle_type
        CHECK (
            courier_vehicle_type IS NULL
            OR courier_vehicle_type IN ('BICYCLE', 'MOTORBIKE', 'CAR', 'VAN')
        );
