package Wallet_api.dto;

import Wallet_api.entity.AccountStatus;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String ownerEmail,
        BigDecimal balance,
        AccountStatus status,
        Long version
) {
}