package Wallet_api.service;

import Wallet_api.dto.TransactionHistoryItem;
import Wallet_api.entity.Account;
import Wallet_api.entity.WalletTransaction;
import Wallet_api.repository.AccountRepository;
import Wallet_api.repository.WalletTransactionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class TransactionHistoryService {

    private final AccountRepository accountRepository;
    private final WalletTransactionRepository transactionRepository;

    public TransactionHistoryService(
            AccountRepository accountRepository,
            WalletTransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionHistoryItem> getHistory(
            String userEmail,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate must be earlier than or equal to endDate"
            );
        }

        Account account = accountRepository.findByUser_Email(userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet account not found"
                ));

        return transactionRepository.findHistory(
                account.getId(),
                startDate,
                endDate,
                pageable
        ).map(this::toHistoryItem);
    }

    private TransactionHistoryItem toHistoryItem(
            WalletTransaction transaction
    ) {
        String fromEmail = transaction.getFromAccount() == null
                ? null
                : transaction.getFromAccount().getUser().getEmail();

        String toEmail = transaction.getToAccount() == null
                ? null
                : transaction.getToAccount().getUser().getEmail();

        String fromPhoneNumber = transaction.getFromAccount() == null
                ? null
                : transaction.getFromAccount().getUser().getPhoneNumber();

        String toPhoneNumber = transaction.getToAccount() == null
                ? null
                : transaction.getToAccount().getUser().getPhoneNumber();

        return new TransactionHistoryItem(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getStatus(),
                fromEmail,
                toEmail,
                fromPhoneNumber,
                toPhoneNumber,
                transaction.getCreatedAt()
        );
    }
}
