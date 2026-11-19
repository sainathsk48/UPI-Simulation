package Wallet_api.controller;

import Wallet_api.dto.MoneyRequest;
import Wallet_api.dto.TransactionHistoryItem;
import Wallet_api.dto.TransactionResponse;
import Wallet_api.dto.TransferRequest;
import Wallet_api.service.TransactionHistoryService;
import Wallet_api.service.WalletTransactionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final WalletTransactionService transactionService;
    private final TransactionHistoryService historyService;

    public TransactionController(
            WalletTransactionService transactionService,
            TransactionHistoryService historyService
    ) {
        this.transactionService = transactionService;
        this.historyService = historyService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MoneyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        TransactionResponse response = transactionService.deposit(
                jwt.getSubject(),
                request.amount(),
                idempotencyKey
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MoneyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        TransactionResponse response = transactionService.withdraw(
                jwt.getSubject(),
                request.amount(),
                idempotencyKey
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        TransactionResponse response = transactionService.transfer(
                jwt.getSubject(),
                request,
                idempotencyKey
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public Page<TransactionHistoryItem> getTransactionHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate,
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
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return historyService.getHistory(
                jwt.getSubject(),
                startDate,
                endDate,
                pageable
        );
    }
}