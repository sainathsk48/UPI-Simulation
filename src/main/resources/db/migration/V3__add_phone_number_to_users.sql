ALTER TABLE users
    ADD COLUMN phone_number VARCHAR(20) NULL;

CREATE UNIQUE INDEX uq_users_phone_number
    ON users (phone_number);