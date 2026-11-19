package Wallet_api.service;

import Wallet_api.dto.AccountResponse;
import Wallet_api.entity.Account;
import Wallet_api.repository.AccountRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAccountService {

    private final AccountRepository accountRepository;

    public AdminAccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> getAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        Account account = findAccount(accountId);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse freezeAccount(Long accountId) {
        Account account = findAccount(accountId);
        account.freeze();

        Account savedAccount = accountRepository.saveAndFlush(account);
        return toResponse(savedAccount);
    }

    @Transactional
    public AccountResponse unfreezeAccount(Long accountId) {
        Account account = findAccount(accountId);
        account.unfreeze();

        Account savedAccount = accountRepository.saveAndFlush(account);
        return toResponse(savedAccount);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet account not found"
                ));
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getUser().getEmail(),
                account.getBalance(),
                account.getStatus(),
                account.getVersion()
        );
    }
}