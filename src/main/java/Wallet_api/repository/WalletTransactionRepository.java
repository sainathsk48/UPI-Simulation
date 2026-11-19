package Wallet_api.repository;

import Wallet_api.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
    SELECT transaction
    FROM WalletTransaction transaction
    WHERE (
        transaction.fromAccount.id = :accountId
        OR transaction.toAccount.id = :accountId
    )
    AND (:startDate IS NULL OR transaction.createdAt >= :startDate)
    AND (:endDate IS NULL OR transaction.createdAt <= :endDate)
    """)
    Page<WalletTransaction> findHistory(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}