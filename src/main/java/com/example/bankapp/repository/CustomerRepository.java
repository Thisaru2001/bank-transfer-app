package com.example.bankapp.repository;

import com.example.bankapp.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * 1. Finds one customer by account number using JPQL and @Param.
     */
    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :accountNumber")
    Optional<Customer> getByAccountNumber(@Param("accountNumber") String accountNumber);

    /**
     * 2a. Checks whether an account already exists.
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.accountNumber = :accountNumber")
    long countAccounts(@Param("accountNumber") String accountNumber);

    /**
     * 2b. Checks whether an email already exists.
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.email = :email")
    long countEmails(@Param("email") String email);

    /**
     * 3. All customers sorted by name using JPQL ORDER BY.
     */
    @Query("SELECT c FROM Customer c ORDER BY c.fullName ASC")
    List<Customer> findAllOrdered();

    /**
     * 4. Case-insensitive customer search using JPQL LIKE.
     */
    @Query("SELECT c FROM Customer c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY c.fullName ASC")
    List<Customer> searchByName(@Param("keyword") String keyword);

    /**
     * 5. Sum of all customer balances (JPQL aggregate, null if no customers).
     */
    @Query("SELECT SUM(c.balance) FROM Customer c")
    BigDecimal getTotalBalance();

    /**
     * 6. Plain MySQL native query counting total customers.
     */
    @Query(value = "SELECT COUNT(*) FROM customers", nativeQuery = true)
    long countCustomersNative();

    /**
     * 7. Atomic debit: subtracts money only if balance >= :amount.
     * Returns the number of affected rows (1 if successful, 0 if insufficient balance).
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Customer c SET c.balance = c.balance - :amount WHERE c.accountNumber = :accountNumber AND c.balance >= :amount")
    int debit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    /**
     * 8. Credit: adds money to the specified account.
     * Returns the number of affected rows.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Customer c SET c.balance = c.balance + :amount WHERE c.accountNumber = :accountNumber")
    int credit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);
}
