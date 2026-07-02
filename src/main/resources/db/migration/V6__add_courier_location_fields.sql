ALTER TABLE users
    ADD COLUMN courier_latitude NUMERIC(8, 6),
    ADD COLUMN courier_longitude NUMERIC(9, 6),
    ADD CONSTRAINT chk_users_courier_latitude
        CHECK (
            courier_latitude IS NULL
            OR (courier_latitude >= -90 AND courier_latitude <= 90)
        ),
    ADD CONSTRAINT chk_users_courier_longitude
        CHECK (
            courier_longitude IS NULL
            OR (courier_longitude >= -180 AND courier_longitude <= 180)
        ),
    ADD CONSTRAINT chk_users_courier_location_pair
        CHECK (
            (courier_latitude IS NULL AND courier_longitude IS NULL)
            OR (courier_latitude IS NOT NULL AND courier_longitude IS NOT NULL)
        ),
    ADD CONSTRAINT chk_users_courier_location_role
        CHECK (
            role = 'COURIER'
            OR (courier_latitude IS NULL AND courier_longitude IS NULL)
        );
