package com.example.bankapp.service;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Saves a FAILED record after a transfer was rolled back.
 * REQUIRES_NEW = always start a brand-new, independent transaction,
 * so this record is committed even though the transfer itself was rolled back.
 *
 * This must be a separate Spring service class to ensure that the invocation
 * goes through the Spring proxy rather than being bypassed by self-invocation.
 */
@Service
@RequiredArgsConstructor
public class TransactionAuditService {

    private final BankTransactionRepository transactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankTransaction recordFailure(String fromAccount, String toAccount, BigDecimal amount, String reason) {
        BankTransaction tx = new BankTransaction(
                fromAccount,
                toAccount,
                amount != null ? amount : BigDecimal.ZERO,
                LocalDateTime.now(),
                reason != null ? reason : "Transaction failed",
                TransactionStatus.FAILED
        );
        return transactionRepository.save(tx);
    }
}
