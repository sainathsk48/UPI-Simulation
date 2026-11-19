package Wallet_api.dto;

import Wallet_api.entity.Role;

public record RegisterResponse(
        Long id,
        String name,
        String email,
        String phoneNumber,
        Role role
) {
}
