CREATE TABLE users (
                       id BIGINT NOT NULL AUTO_INCREMENT,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       role VARCHAR(20) NOT NULL DEFAULT 'USER',
                       created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                       PRIMARY KEY (id),
                       CONSTRAINT uq_users_email UNIQUE (email),
                       CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE accounts (
                          id BIGINT NOT NULL AUTO_INCREMENT,
                          user_id BIGINT NOT NULL,
                          balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
                          version BIGINT NOT NULL DEFAULT 0,
                          status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                          created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                          PRIMARY KEY (id),
                          CONSTRAINT uq_accounts_user_id UNIQUE (user_id),
                          CONSTRAINT fk_accounts_user
                              FOREIGN KEY (user_id) REFERENCES users(id),
                          CONSTRAINT chk_accounts_balance CHECK (balance >= 0.00),
                          CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN'))
) ENGINE=InnoDB;