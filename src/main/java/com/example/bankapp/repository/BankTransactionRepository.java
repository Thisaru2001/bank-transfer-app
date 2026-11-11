package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    /**
     * 1. Returns all transactions, ordered newest first.
     */
    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findAllLatest();

    /**
     * 2. Returns transaction history where the account is either sender or receiver (OR condition).
     */
    @Query("SELECT t FROM BankTransaction t WHERE t.fromAccount = :accountNumber OR t.toAccount = :accountNumber ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findHistory(@Param("accountNumber") String accountNumber);

    /**
     * 3. Filters transactions by status, newest first.
     */
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    /**
     * 4. Counts transactions with a specific status (SUCCESS or FAILED) for dashboard stats.
     */
    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    /**
     * Task 12 Extension: Returns successful transfers with amount greater than or equal to minAmount.
     */
    @Query("SELECT t FROM BankTransaction t WHERE t.status = com.example.bankapp.model.TransactionStatus.SUCCESS AND t.amount >= :minAmount ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findLargeTransfers(@Param("minAmount") BigDecimal minAmount);

    /**
     * Task 12 Extension: Returns the total amount successfully sent by a specific account.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BankTransaction t WHERE t.fromAccount = :accountNumber AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS")
    BigDecimal getTotalSentByAccount(@Param("accountNumber") String accountNumber);
}
