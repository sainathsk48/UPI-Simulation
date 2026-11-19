package Wallet_api.service;

import Wallet_api.dto.TransactionResponse;
import Wallet_api.dto.TransferRequest;
import Wallet_api.entity.Account;
import Wallet_api.entity.AccountStatus;
import Wallet_api.entity.TransactionType;
import Wallet_api.entity.WalletTransaction;
import Wallet_api.repository.AccountRepository;
import Wallet_api.repository.WalletTransactionRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class WalletTransactionService {

    private final AccountRepository accountRepository;
    private final WalletTransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;

    public WalletTransactionService(
            AccountRepository accountRepository,
            WalletTransactionRepository transactionRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public TransactionResponse deposit(
            String userEmail,
            BigDecimal amount,
            String idempotencyKey
    ) {
        validateAmount(amount);
        String key = validateIdempotencyKey(idempotencyKey);

        Account account = accountRepository.findByUserEmailForUpdate(userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet account not found"
                ));

        var previous = transactionRepository.findByIdempotencyKey(key);

        if (previous.isPresent()) {
            WalletTransaction transaction = previous.get();

            boolean sameDeposit =
                    transaction.getType() == TransactionType.DEPOSIT
                            && transaction.getToAccount() != null
                            && transaction.getToAccount().getId()
                            .equals(account.getId())
                            && transaction.getAmount().compareTo(amount) == 0;

            if (!sameDeposit) {
                throw idempotencyConflict();
            }

            return toResponse(transaction);
        }

        ensureAccountIsActive(account);

        account.credit(amount);

        WalletTransaction transaction = new WalletTransaction(
                null,
                account,
                amount,
                TransactionType.DEPOSIT,
                key
        );

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse withdraw(
            String userEmail,
            BigDecimal amount,
            String idempotencyKey
    ) {
        validateAmount(amount);
        String key = validateIdempotencyKey(idempotencyKey);

        // Lock the account so simultaneous withdrawals cannot overspend it.
        Account account = accountRepository.findByUserEmailForUpdate(userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet account not found"
                ));

        var previous = transactionRepository.findByIdempotencyKey(key);

        if (previous.isPresent()) {
            WalletTransaction transaction = previous.get();

            boolean sameWithdrawal =
                    transaction.getType() == TransactionType.WITHDRAWAL
                            && transaction.getFromAccount() != null
                            && transaction.getFromAccount().getId()
                            .equals(account.getId())
                            && transaction.getToAccount() == null
                            && transaction.getAmount().compareTo(amount) == 0;

            if (!sameWithdrawal) {
                throw idempotencyConflict();
            }

            return toResponse(transaction);
        }

        ensureAccountIsActive(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient funds"
            );
        }

        account.debit(amount);

        WalletTransaction transaction = new WalletTransaction(
                account,
                null,
                amount,
                TransactionType.WITHDRAWAL,
                key
        );

        return toResponse(transactionRepository.save(transaction));
    }

    public TransactionResponse transfer(
            String senderEmail,
            TransferRequest request,
            String idempotencyKey
    ) {
        try {
            return transactionTemplate.execute(status ->
                    transferInTransaction(senderEmail, request, idempotencyKey)
            );
        } catch (DataIntegrityViolationException exception) {
            /*
             * A simultaneous request may have inserted this key first.
             * TransactionTemplate rolls back this attempt before this catch
             * block runs. Look up and return the transaction that committed.
             */
            return transactionTemplate.execute(status ->
                    returnExistingTransfer(
                            senderEmail,
                            request,
                            idempotencyKey,
                            exception
                    )
            );
        }
    }

    private TransactionResponse transferInTransaction(
            String senderEmail,
            TransferRequest request,
            String idempotencyKey
    ) {
        BigDecimal amount = request.amount();
        validateAmount(amount);
        String key = validateIdempotencyKey(idempotencyKey);

        String fromEmail = normalizeEmail(senderEmail);
        String toPhoneNumber = normalizePhoneNumber(
                request.recipientPhoneNumber()
        );

        Account senderSnapshot = accountRepository.findByUser_Email(fromEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sender wallet account not found"
                ));
        Account recipientSnapshot = accountRepository
                .findByUser_PhoneNumber(toPhoneNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No wallet is registered with that phone number"
                ));

        if (senderSnapshot.getId().equals(recipientSnapshot.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot transfer money to yourself"
            );
        }

        Account sender;
        Account recipient;

        // Lock account rows in ID order so concurrent transfers avoid deadlocks.
        if (senderSnapshot.getId() < recipientSnapshot.getId()) {
            sender = findAccountForUpdate(senderSnapshot.getId());
            recipient = findAccountForUpdate(recipientSnapshot.getId());
        } else {
            recipient = findAccountForUpdate(recipientSnapshot.getId());
            sender = findAccountForUpdate(senderSnapshot.getId());
        }

        var previous = transactionRepository.findByIdempotencyKey(key);

        if (previous.isPresent()) {
            WalletTransaction transaction = previous.get();

            boolean sameTransfer =
                    transaction.getType() == TransactionType.TRANSFER
                            && transaction.getFromAccount() != null
                            && transaction.getFromAccount().getId()
                            .equals(sender.getId())
                            && transaction.getToAccount() != null
                            && transaction.getToAccount().getId()
                            .equals(recipient.getId())
                            && transaction.getAmount().compareTo(amount) == 0;

            if (!sameTransfer) {
                throw idempotencyConflict();
            }

            return toResponse(transaction);
        }

        ensureAccountIsActive(sender);
        ensureAccountIsActive(recipient);

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient funds"
            );
        }

        sender.debit(amount);
        recipient.credit(amount);

        WalletTransaction transaction = new WalletTransaction(
                sender,
                recipient,
                amount,
                TransactionType.TRANSFER,
                key
        );

        return toResponse(transactionRepository.save(transaction));
    }

    private TransactionResponse returnExistingTransfer(
            String senderEmail,
            TransferRequest request,
            String idempotencyKey,
            DataIntegrityViolationException originalException
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw originalException;
        }

        String key = idempotencyKey.trim();
        String fromEmail = normalizeEmail(senderEmail);
        String toPhoneNumber = normalizePhoneNumber(
                request.recipientPhoneNumber()
        );

        WalletTransaction existing = transactionRepository
                .findByIdempotencyKey(key)
                .orElseThrow(() -> originalException);

        String savedFromEmail = existing.getFromAccount() == null
                ? null
                : normalizeEmail(
                existing.getFromAccount().getUser().getEmail()
        );

        String savedToPhoneNumber = existing.getToAccount() == null
                ? null
                : normalizePhoneNumber(
                existing.getToAccount().getUser().getPhoneNumber()
        );

        boolean sameTransfer =
                existing.getType() == TransactionType.TRANSFER
                        && fromEmail.equals(savedFromEmail)
                        && toPhoneNumber.equals(savedToPhoneNumber)
                        && existing.getAmount()
                        .compareTo(request.amount()) == 0;

        if (!sameTransfer) {
            throw idempotencyConflict();
        }

        return toResponse(existing);
    }

    private Account findAccountForUpdate(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet account not found"
                ));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount must be greater than zero"
            );
        }
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key is required"
            );
        }

        return idempotencyKey.trim();
    }

    private void ensureAccountIsActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This wallet account is frozen"
            );
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.trim();
    }

    private ResponseStatusException idempotencyConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This idempotency key was already used for a different request"
        );
    }

    private TransactionResponse toResponse(WalletTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getIdempotencyKey()
        );
    }
}
