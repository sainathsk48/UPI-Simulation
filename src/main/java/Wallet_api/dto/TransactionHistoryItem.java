package Wallet_api.dto;

import Wallet_api.entity.TransactionStatus;
import Wallet_api.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionHistoryItem(
        Long transactionId,
        TransactionType type,
        BigDecimal amount,
        TransactionStatus status,
        String fromEmail,
        String toEmail,
        String fromPhoneNumber,
        String toPhoneNumber,
        LocalDateTime createdAt
) {
}
