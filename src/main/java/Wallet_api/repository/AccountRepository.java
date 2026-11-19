package Wallet_api.repository;

import Wallet_api.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUser_Id(Long userId);
    Optional<Account> findByUser_Email(String email);
    Optional<Account> findByUser_PhoneNumber(String phoneNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.user.email = :email")
    Optional<Account> findByUserEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a join fetch a.user where a.id = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);
}
