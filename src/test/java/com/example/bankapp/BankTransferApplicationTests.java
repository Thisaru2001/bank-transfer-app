package com.example.bankapp;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BankTransferApplicationTests {

    @Autowired
    private BankService bankService;

    @Autowired
    private TransactionAuditService auditService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @BeforeEach
    void setupInitialDatabaseState() {
        transactionRepository.deleteAll();
        customerRepository.deleteAll();

        // Initial sample data as specified in Task 8:
        // Kamal (100001): 50,000.00
        // Nimali (100002): 25,000.00
        // Saman (100003): 10,000.00
        // Bank Fee Account (999999): 0.00 (Task 12 Extension)
        // Total money = 85,000.00
        customerRepository.save(new Customer("Kamal", "100001", "kamal@example.com", new BigDecimal("50000.00")));
        customerRepository.save(new Customer("Nimali", "100002", "nimali@example.com", new BigDecimal("25000.00")));
        customerRepository.save(new Customer("Saman", "100003", "saman@example.com", new BigDecimal("10000.00")));
        customerRepository.save(new Customer("Bank Central Fee Account", "999999", "bank@example.com", new BigDecimal("0.00")));
    }

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
        assertNotNull(bankService);
        assertNotNull(auditService);
        assertNotNull(customerRepository);
        assertNotNull(transactionRepository);
    }

    @Test
    @DisplayName("Test 1: Transfer 5,000 from Kamal to Nimali (Commit all steps saved)")
    void test1_TransferSuccessCommit() {
        TransferRequest request = new TransferRequest("100001", "100002", new BigDecimal("5000.00"), false);
        BankTransaction tx = bankService.transfer(request);

        assertNotNull(tx);
        assertEquals(TransactionStatus.SUCCESS, tx.getStatus());

        Customer kamal = bankService.getCustomerByAccount("100001");
        Customer nimali = bankService.getCustomerByAccount("100002");
        Customer saman = bankService.getCustomerByAccount("100003");

        assertEquals(new BigDecimal("45000.00"), kamal.getBalance());
        assertEquals(new BigDecimal("30000.00"), nimali.getBalance());
        assertEquals(new BigDecimal("10000.00"), saman.getBalance());
        assertEquals(new BigDecimal("85000.00"), bankService.getTotalBalance());

        List<BankTransaction> latest = bankService.getLatestTransactions();
        assertEquals(1, latest.size());
        assertEquals(TransactionStatus.SUCCESS, latest.get(0).getStatus());
    }

    @Test
    @DisplayName("Test 2: Transfer 20,000 from Saman (10,000 balance) to Kamal (Insufficient balance -> Rollback & FAILED audit)")
    void test2_InsufficientBalanceRollback() {
        TransferRequest request = new TransferRequest("100003", "100001", new BigDecimal("20000.00"), false);

        assertThrows(InsufficientBalanceException.class, () -> {
            try {
                bankService.transfer(request);
            } catch (Exception e) {
                auditService.recordFailure(request.getFromAccount(), request.getToAccount(), request.getAmount(), e.getMessage());
                throw e;
            }
        });

        // Balances remain unchanged
        assertEquals(new BigDecimal("50000.00"), bankService.getCustomerByAccount("100001").getBalance());
        assertEquals(new BigDecimal("10000.00"), bankService.getCustomerByAccount("100003").getBalance());
        assertEquals(new BigDecimal("85000.00"), bankService.getTotalBalance());

        // FAILED audit record created independently via REQUIRES_NEW
        List<BankTransaction> failedList = transactionRepository.findByStatusLatest(TransactionStatus.FAILED);
        assertEquals(1, failedList.size());
        assertEquals("100003", failedList.get(0).getFromAccount());
        assertTrue(failedList.get(0).getDescription().contains("Insufficient balance"));
    }

    @Test
    @DisplayName("Test 3: Select same sender and receiver account (Validation inside transaction)")
    void test3_SameAccountValidationRollback() {
        TransferRequest request = new TransferRequest("100001", "100001", new BigDecimal("2000.00"), false);

        assertThrows(BankException.class, () -> {
            try {
                bankService.transfer(request);
            } catch (Exception e) {
                auditService.recordFailure(request.getFromAccount(), request.getToAccount(), request.getAmount(), e.getMessage());
                throw e;
            }
        });

        assertEquals(new BigDecimal("50000.00"), bankService.getCustomerByAccount("100001").getBalance());
        List<BankTransaction> failedList = transactionRepository.findByStatusLatest(TransactionStatus.FAILED);
        assertEquals(1, failedList.size());
        assertTrue(failedList.get(0).getDescription().contains("must be different"));
    }

    @Test
    @DisplayName("Test 4: Transfer 1,000 from Kamal to Saman with simulated failure (Withdrawal UNDONE by Rollback)")
    void test4_SimulateFailureRollback() {
        TransferRequest request = new TransferRequest("100001", "100003", new BigDecimal("1000.00"), true);

        assertThrows(BankException.class, () -> {
            try {
                bankService.transfer(request);
            } catch (Exception e) {
                auditService.recordFailure(request.getFromAccount(), request.getToAccount(), request.getAmount(), e.getMessage());
                throw e;
            }
        });

        // Crucial verification: Kamal's balance remains 50,000.00, withdrawal was completely undone!
        assertEquals(new BigDecimal("50000.00"), bankService.getCustomerByAccount("100001").getBalance());
        assertEquals(new BigDecimal("10000.00"), bankService.getCustomerByAccount("100003").getBalance());
        assertEquals(new BigDecimal("85000.00"), bankService.getTotalBalance());

        // FAILED audit record preserved independently
        List<BankTransaction> failedList = transactionRepository.findByStatusLatest(TransactionStatus.FAILED);
        assertEquals(1, failedList.size());
        assertTrue(failedList.get(0).getDescription().contains("Simulated system failure"));
    }

    @Test
    @DisplayName("Test 6: Add customer and reject duplicate account number")
    void test6_AddCustomerAndDuplicateCheck() {
        Customer nuwan = new Customer("Nuwan", "100004", "nuwan@example.com", new BigDecimal("15000.00"));
        Customer saved = bankService.createCustomer(nuwan);
        assertNotNull(saved.getId());

        Customer duplicateAccount = new Customer("Nuwan Silva", "100004", "nuwansilva@example.com", new BigDecimal("5000.00"));
        assertThrows(BankException.class, () -> bankService.createCustomer(duplicateAccount));
    }

    @Test
    @DisplayName("Test 7: Search by name and retrieve customer history")
    void test7_SearchByNameAndHistory() {
        List<Customer> searchResult = bankService.searchCustomers("kam");
        assertEquals(1, searchResult.size());
        assertEquals("Kamal", searchResult.get(0).getFullName());

        // Perform a transfer and a failed transfer for Kamal
        bankService.transfer(new TransferRequest("100001", "100002", new BigDecimal("5000.00"), false));
        auditService.recordFailure("100001", "100003", new BigDecimal("20000.00"), "Simulated history failure");

        List<BankTransaction> history = bankService.getAccountHistory("100001");
        assertEquals(2, history.size());
    }

    @Test
    @DisplayName("Task 12 Extension: Large transfers query")
    void testTask12_LargeTransfersQuery() {
        bankService.transfer(new TransferRequest("100001", "100002", new BigDecimal("15000.00"), false));
        bankService.transfer(new TransferRequest("100001", "100003", new BigDecimal("5000.00"), false));

        List<BankTransaction> largeTransfers = bankService.getLargeTransfers(new BigDecimal("10000.00"));
        assertEquals(1, largeTransfers.size());
        assertEquals(new BigDecimal("15000.00"), largeTransfers.get(0).getAmount());
    }

    @Test
    @DisplayName("Task 12 Extension: Total sent amount query")
    void testTask12_TotalSentQuery() {
        bankService.transfer(new TransferRequest("100001", "100002", new BigDecimal("5000.00"), false));
        bankService.transfer(new TransferRequest("100001", "100003", new BigDecimal("7000.00"), false));

        BigDecimal totalSent = bankService.getTotalSent("100001");
        assertEquals(new BigDecimal("12000.00"), totalSent);
    }

    @Test
    @DisplayName("Task 12 Extension: Deposit and Withdrawal operations")
    void testTask12_DepositAndWithdrawal() {
        // Deposit 5,000 into Kamal
        bankService.deposit("100001", new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("55000.00"), bankService.getCustomerByAccount("100001").getBalance());

        // Withdraw 2,000 from Kamal
        bankService.withdraw("100001", new BigDecimal("2000.00"));
        assertEquals(new BigDecimal("53000.00"), bankService.getCustomerByAccount("100001").getBalance());

        // Withdraw more than balance -> throws InsufficientBalanceException
        assertThrows(InsufficientBalanceException.class, () ->
                bankService.withdraw("100001", new BigDecimal("100000.00")));
    }

    @Test
    @DisplayName("Task 12 Extension: Transfer with 1% bank fee credited to 999999")
    void testTask12_TransferFeeAtomic() {
        // Kamal sends 5,000 to Nimali with applyFee = true
        // 1% of 5,000 = 50.00 fee
        // Kamal debited: 5,000 + 50 = 5,050.00 -> balance becomes 50,000 - 5,050 = 44,950.00
        // Nimali credited: 5,000.00 -> balance becomes 25,000 + 5,000 = 30,000.00
        // Bank (999999) credited: 50.00 -> balance becomes 0 + 50 = 50.00
        TransferRequest request = new TransferRequest("100001", "100002", new BigDecimal("5000.00"), false, true);
        BankTransaction tx = bankService.transfer(request);

        assertNotNull(tx);
        assertTrue(tx.getDescription().contains("1% Fee: LKR 50.00"));

        assertEquals(new BigDecimal("44950.00"), bankService.getCustomerByAccount("100001").getBalance());
        assertEquals(new BigDecimal("30000.00"), bankService.getCustomerByAccount("100002").getBalance());
        assertEquals(new BigDecimal("50.00"), bankService.getCustomerByAccount("999999").getBalance());

        // Total money in system remains conserved at 85,000.00
        assertEquals(new BigDecimal("85000.00"), bankService.getTotalBalance());
    }
}
