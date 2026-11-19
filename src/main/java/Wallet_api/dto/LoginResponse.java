package Wallet_api.dto;

import Wallet_api.entity.Role;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        Long userId,
        String email,
        Role role
) {
}