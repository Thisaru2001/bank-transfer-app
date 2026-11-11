package com.example.bankapp.service;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // default for every method: read-only transaction
public class BankService {

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    public List<Customer> getAllCustomers() {
        return customerRepository.findAllOrdered();
    }

    public List<Customer> searchCustomers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllCustomers();
        }
        return customerRepository.searchByName(keyword.trim());
    }

    public Customer getCustomerByAccount(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    public BigDecimal getTotalBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total != null ? total : BigDecimal.ZERO;
    }

    public long countCustomers() {
        return customerRepository.countCustomersNative();
    }

    public List<BankTransaction> getLatestTransactions() {
        return transactionRepository.findAllLatest();
    }

    public long countSuccessfulTransfers() {
        return transactionRepository.countByStatus(TransactionStatus.SUCCESS);
    }

    public long countFailedTransfers() {
        return transactionRepository.countByStatus(TransactionStatus.FAILED);
    }

    public List<BankTransaction> getAccountHistory(String accountNumber) {
        return transactionRepository.findHistory(accountNumber);
    }

    public BigDecimal getTotalSent(String accountNumber) {
        BigDecimal total = transactionRepository.getTotalSentByAccount(accountNumber);
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<BankTransaction> getLargeTransfers(BigDecimal minAmount) {
        BigDecimal threshold = minAmount != null ? minAmount : new BigDecimal("10000.00");
        return transactionRepository.findLargeTransfers(threshold);
    }

    /**
     * Creates a new customer ensuring no duplicate account number or email exists.
     */
    @Transactional(rollbackFor = Exception.class)
    public Customer createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account number " + customer.getAccountNumber() + " already exists");
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankException("Email " + customer.getEmail() + " already exists");
        }
        return customerRepository.save(customer);
    }

    /**
     * Executes a transactional bank transfer adhering to ACID principles.
     * Steps:
     * 1. Validate sender and receiver accounts are different and both exist.
     * 2. Validate transfer amount.
     * 3. Compute 1% transfer fee if fee option applies.
     * 4. Debit sender with total amount (amount + fee).
     * 5. If simulated failure is checked, throw an exception immediately.
     * 6. Credit receiver with transfer amount.
     * 7. Credit bank reserve (999999) with fee if applied.
     * 8. Record and return SUCCESS transaction in bank_transactions.
     */
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(TransferRequest request) {
        String from = request.getFromAccount();
        String to = request.getToAccount();
        BigDecimal amount = request.getAmount();

        // Step 1: Validate accounts
        if (from == null || from.trim().isEmpty() || to == null || to.trim().isEmpty()) {
            throw new BankException("Sender and receiver accounts cannot be empty");
        }
        if (from.trim().equals(to.trim())) {
            throw new BankException("Sender and receiver accounts must be different");
        }

        Customer sender = customerRepository.getByAccountNumber(from.trim())
                .orElseThrow(() -> new AccountNotFoundException(from.trim()));
        Customer receiver = customerRepository.getByAccountNumber(to.trim())
                .orElseThrow(() -> new AccountNotFoundException(to.trim()));

        // Step 2: Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Transfer amount must be greater than zero");
        }

        // Step 3: Fee calculation (Task 12 Extension)
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal totalDebit = amount;
        if (request.isApplyFee()) {
            fee = amount.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            totalDebit = amount.add(fee);
        }

        // Step 4: Atomic debit of sender
        int rowsUpdated = customerRepository.debit(sender.getAccountNumber(), totalDebit);
        if (rowsUpdated == 0) {
            throw new InsufficientBalanceException(sender.getAccountNumber());
        }

        // Step 5: Simulated failure trigger (Task 9 & Task 10 verification)
        if (request.isSimulateFailure()) {
            throw new BankException("Simulated system failure after withdrawal! All changes were rolled back.");
        }

        // Step 6: Credit receiver
        customerRepository.credit(receiver.getAccountNumber(), amount);

        // Step 7: Credit bank fee account if fee was applied
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            customerRepository.credit("999999", fee);
        }

        // Step 8: Save SUCCESS transaction record
        String description = "Transfer completed";
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            description = "Transfer completed (1% Fee: LKR " + fee + " credited to 999999)";
        }

        BankTransaction tx = new BankTransaction(
                sender.getAccountNumber(),
                receiver.getAccountNumber(),
                amount,
                LocalDateTime.now(),
                description,
                TransactionStatus.SUCCESS
        );
        return transactionRepository.save(tx);
    }

    /**
     * Task 12 Extension: Deposit funds into a customer account.
     */
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Deposit amount must be greater than zero");
        }
        Customer customer = customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        customerRepository.credit(customer.getAccountNumber(), amount);

        BankTransaction tx = new BankTransaction(
                null,
                customer.getAccountNumber(),
                amount,
                LocalDateTime.now(),
                "Deposit to account " + customer.getAccountNumber(),
                TransactionStatus.SUCCESS
        );
        return transactionRepository.save(tx);
    }

    /**
     * Task 12 Extension: Withdraw funds from a customer account.
     */
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Withdrawal amount must be greater than zero");
        }
        Customer customer = customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        int rows = customerRepository.debit(customer.getAccountNumber(), amount);
        if (rows == 0) {
            throw new InsufficientBalanceException(customer.getAccountNumber());
        }

        BankTransaction tx = new BankTransaction(
                customer.getAccountNumber(),
                null,
                amount,
                LocalDateTime.now(),
                "Withdrawal from account " + customer.getAccountNumber(),
                TransactionStatus.SUCCESS
        );
        return transactionRepository.save(tx);
    }
}
