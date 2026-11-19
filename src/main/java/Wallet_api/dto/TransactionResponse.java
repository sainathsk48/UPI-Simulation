package Wallet_api.dto;

import Wallet_api.entity.TransactionStatus;
import Wallet_api.entity.TransactionType;

import java.math.BigDecimal;

public record TransactionResponse(
        Long transactionId,
        TransactionType type,
        BigDecimal amount,
        TransactionStatus status,
        String idempotencyKey
) {
}