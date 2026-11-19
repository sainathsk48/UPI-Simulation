package Wallet_api.controller;

import Wallet_api.dto.AccountResponse;
import Wallet_api.service.AccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public AccountResponse getMyAccount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.getAccountByEmail(jwt.getSubject());
    }
}