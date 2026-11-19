CREATE TABLE wallet_transactions (
                                     id BIGINT NOT NULL AUTO_INCREMENT,
                                     from_account_id BIGINT NULL,
                                     to_account_id BIGINT NULL,
                                     amount DECIMAL(19,2) NOT NULL,
                                     type VARCHAR(20) NOT NULL,
                                     status VARCHAR(20) NOT NULL,
                                     idempotency_key VARCHAR(100) NOT NULL,
                                     created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                     PRIMARY KEY (id),

                                     CONSTRAINT uq_wallet_tx_idempotency
                                         UNIQUE (idempotency_key),

                                     CONSTRAINT fk_wallet_tx_from
                                         FOREIGN KEY (from_account_id) REFERENCES accounts(id),

                                     CONSTRAINT fk_wallet_tx_to
                                         FOREIGN KEY (to_account_id) REFERENCES accounts(id),

                                     CONSTRAINT chk_wallet_tx_amount
                                         CHECK (amount > 0),

                                     CONSTRAINT chk_wallet_tx_type
                                         CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),

                                     CONSTRAINT chk_wallet_tx_status
                                         CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

                                     CONSTRAINT chk_wallet_tx_accounts
                                         CHECK (
                                             (type = 'DEPOSIT'
                                                 AND from_account_id IS NULL
                                                 AND to_account_id IS NOT NULL)
                                                 OR
                                             (type = 'WITHDRAWAL'
                                                 AND from_account_id IS NOT NULL
                                                 AND to_account_id IS NULL)
                                                 OR
                                             (type = 'TRANSFER'
                                                 AND from_account_id IS NOT NULL
                                                 AND to_account_id IS NOT NULL
                                                 AND from_account_id <> to_account_id)
                                             ),

                                     INDEX idx_wallet_tx_from_created
                                         (from_account_id, created_at),

                                     INDEX idx_wallet_tx_to_created
                                         (to_account_id, created_at)
) ENGINE=InnoDB;