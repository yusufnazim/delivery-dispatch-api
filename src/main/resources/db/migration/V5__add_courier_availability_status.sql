ALTER TABLE users
    ADD COLUMN courier_availability_status VARCHAR(32);

UPDATE users
SET courier_availability_status = 'UNAVAILABLE'
WHERE role = 'COURIER'
  AND courier_availability_status IS NULL;

ALTER TABLE users
    ADD CONSTRAINT chk_users_courier_availability_status
        CHECK (
            courier_availability_status IS NULL
            OR courier_availability_status IN ('AVAILABLE', 'UNAVAILABLE', 'ON_DELIVERY')
        ),
    ADD CONSTRAINT chk_users_courier_availability_status_role
        CHECK (
            (role = 'COURIER' AND courier_availability_status IS NOT NULL)
            OR (role <> 'COURIER' AND courier_availability_status IS NULL)
        );
