package Wallet_api.controller;

import Wallet_api.dto.AccountResponse;
import Wallet_api.service.AdminAccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    public Page<AccountResponse> getAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be 0 or greater, and size must be between 1 and 100"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "id")
        );

        return adminAccountService.getAccounts(pageable);
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(
            @PathVariable("accountId") Long accountId
    ) {
        return adminAccountService.getAccount(accountId);
    }

    @PatchMapping("/{accountId}/freeze")
    public AccountResponse freezeAccount(
            @PathVariable("accountId") Long accountId
    ) {
        return adminAccountService.freezeAccount(accountId);
    }

    @PatchMapping("/{accountId}/unfreeze")
    public AccountResponse unfreezeAccount(
            @PathVariable("accountId") Long accountId
    ) {
        return adminAccountService.unfreezeAccount(accountId);
    }
}