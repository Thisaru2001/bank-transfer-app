# University of Kelaniya
## Department of Statistics & Computer Science
### Academic Year: 2024/2025 (Semester II)
### Course: COSC 32133 / BECS 32263 – Full-Stack Software Development
### Assignment 01: Bank Transactions with @Transactional, Rollback and @Query

---

**Student Name:** `[YOUR_NAME_HERE]`  
**Student ID:** `[YOUR_STUDENT_ID_HERE]`  
**Submission Date:** 11th of November 2026  
**GitHub Repository:** [https://github.com/Thisaru2001/bank-transfer-app](https://github.com/Thisaru2001/bank-transfer-app)  
**Target Submission File:** `Assignment01_[YOUR_STUDENT_ID].pdf`

---

## 1. Executive Summary & Scenario Overview
A banking system demands strict adherence to consistency and data integrity. In a fund transfer workflow, money is debited from a sender and credited to a recipient. If a system failure or network disruption occurs midway through the operation—after funds have been withdrawn from the sender but prior to credit reaching the receiver—uncommitted state changes would cause funds to vanish permanently.

This project delivers a complete enterprise-grade Spring Boot full-stack banking application that implements:
1. **ACID Transaction Management**: Powered by Spring's `@Transactional(rollbackFor = Exception.class)` to guarantee all-or-nothing execution.
2. **Spring Dynamic Proxy Architecture**: Ensuring transactions are intercepted and managed transparently.
3. **Optimized Persistence with `@Query`**: Custom JPQL queries, native MySQL queries, and atomic `@Modifying` queries.
4. **Independent Failure Auditing**: Utilizing `Propagation.REQUIRES_NEW` in an isolated Spring service (`TransactionAuditService`) to record transaction failures in the database even after the primary transfer transaction rolls back.
5. **Task 10 Transaction Isolation Experiment**: Demonstrating the catastrophic financial loss caused when transaction boundaries are omitted (`Propagation.SUPPORTS`) versus full rollback protection.
6. **Task 12 System Extensions**: Supporting large-transfer thresholds, account history totals, deposit/withdrawal operations, and an atomic 1% bank fee routed to institutional account `999999`.

---

## 2. Technology Stack
- **Backend Framework:** Spring Boot 4.x / Spring Web MVC
- **Language / Runtime:** Java 21 (LTS)
- **Persistence & ORM:** Spring Data JPA, Hibernate ORM
- **Database Engine:** MySQL 8.x / MariaDB (InnoDB Engine for full transactional rollback support)
- **Frontend / Templating:** Thymeleaf, Semantic HTML5, Custom CSS3, Responsive Layouts
- **Validation:** Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@DecimalMin`, `@Digits`, `@Pattern`, `@Email`)
- **Boilerplate Reduction:** Project Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`)
- **Build System:** Apache Maven 3.9+
- **Version Control:** Git & GitHub (`main` and `feature/bank-transfer` branches)
- **Precision Financial Arithmetic:** `java.math.BigDecimal` throughout all calculation pathways (never `float` or `double`).

---

## 3. System Architecture & Project Structure
The application follows clean layered architecture with clear separation of concerns:

```
com.example.bankapp
├── BankTransferApplication.java       // Main Spring Boot application entry point
├── config
│   └── DataLoader.java                // CommandLineRunner initializing sample bank accounts
├── controller
│   └── BankController.java            // Spring MVC web controller handling endpoints & flash attributes
├── dto
│   └── TransferRequest.java           // Form DTO with validation annotations and simulation flags
├── exception
│   ├── BankException.java             // Base RuntimeException triggering automatic rollback
│   ├── AccountNotFoundException.java  // Specific error when account does not exist
│   └── InsufficientBalanceException.java // Specific error when sender funds are insufficient
├── model
│   ├── Customer.java                  // Entity mapped to 'customers' table
│   ├── BankTransaction.java           // Entity mapped to 'bank_transactions' table
│   └── TransactionStatus.java         // Enum (SUCCESS, FAILED)
├── repository
│   ├── CustomerRepository.java        // JPA Repository with 8 custom @Query methods
│   └── BankTransactionRepository.java // JPA Repository with 6 custom @Query methods
└── service
    ├── BankService.java               // Core service: @Transactional(readOnly = true) & transfer()
    └── TransactionAuditService.java   // Independent audit service: @Transactional(propagation = REQUIRES_NEW)
```

---

## 4. Database Implementation & Entity Design

### 4.1 Database Engine Requirements
The MySQL database `bank_db` is configured using the InnoDB storage engine. Unlike MyISAM, InnoDB supports ACID transactions, row-level locking, foreign keys, and transaction rollbacks (`ROLLBACK`).

### 4.2 Entity Mappings

#### 1. `Customer` Entity (`customers` table)
- **`id`** (`BIGINT AUTO_INCREMENT PRIMARY KEY`): Unique customer identifier.
- **`fullName`** (`VARCHAR(255) NOT NULL`): Account holder name. Annotated with `@NotBlank`.
- **`accountNumber`** (`VARCHAR(10) NOT NULL UNIQUE`): 6-digit account identifier. Annotated with `@NotBlank`, `@Pattern(regexp = "^[0-9]{6}$")`.
- **`email`** (`VARCHAR(255) NOT NULL UNIQUE`): Email address. Annotated with `@NotBlank`, `@Email`.
- **`balance`** (`DECIMAL(14,2) NOT NULL`): Current monetary balance. Annotated with `@NotNull`, `@DecimalMin("0.00")`, `@Digits(integer=12, fraction=2)`.

#### 2. `BankTransaction` Entity (`bank_transactions` table)
- **`id`** (`BIGINT AUTO_INCREMENT PRIMARY KEY`): Unique transaction log identifier.
- **`fromAccount`** (`VARCHAR(10) NULL`): Sender account number (null for cash deposits).
- **`toAccount`** (`VARCHAR(10) NULL`): Receiver account number (null for cash withdrawals).
- **`amount`** (`DECIMAL(14,2) NOT NULL`): Financial amount transferred.
- **`createdAt`** (`TIMESTAMP(6) NOT NULL`): Exact timestamp of transaction execution.
- **`description`** (`VARCHAR(255) NOT NULL`): Narrative summary or failure cause.
- **`status`** (`VARCHAR(10) NOT NULL`): `@Enumerated(EnumType.STRING)` mapping to `SUCCESS` or `FAILED`.

---

## 5. Repository `@Query` Implementations

### 5.1 `CustomerRepository.java` (8 Custom Queries)

1. **`getByAccountNumber`** (JPQL + `@Param`):
   ```java
   @Query("SELECT c FROM Customer c WHERE c.accountNumber = :accountNumber")
   Optional<Customer> getByAccountNumber(@Param("accountNumber") String accountNumber);
   ```
2. **`countAccounts` & `countEmails`** (JPQL COUNT):
   ```java
   @Query("SELECT COUNT(c) FROM Customer c WHERE c.accountNumber = :accountNumber")
   long countAccounts(@Param("accountNumber") String accountNumber);

   @Query("SELECT COUNT(c) FROM Customer c WHERE c.email = :email")
   long countEmails(@Param("email") String email);
   ```
3. **`findAllOrdered`** (JPQL ORDER BY):
   ```java
   @Query("SELECT c FROM Customer c ORDER BY c.fullName ASC")
   List<Customer> findAllOrdered();
   ```
4. **`searchByName`** (JPQL LIKE):
   ```java
   @Query("SELECT c FROM Customer c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY c.fullName ASC")
   List<Customer> searchByName(@Param("keyword") String keyword);
   ```
5. **`getTotalBalance`** (JPQL aggregate):
   ```java
   @Query("SELECT SUM(c.balance) FROM Customer c")
   BigDecimal getTotalBalance();
   ```
6. **`countCustomersNative`** (Native SQL):
   ```java
   @Query(value = "SELECT COUNT(*) FROM customers", nativeQuery = true)
   long countCustomersNative();
   ```
7. **`debit`** (UPDATE + `@Modifying` + `@Transactional`):
   ```java
   @Modifying(clearAutomatically = true)
   @Transactional
   @Query("UPDATE Customer c SET c.balance = c.balance - :amount WHERE c.accountNumber = :accountNumber AND c.balance >= :amount")
   int debit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);
   ```
8. **`credit`** (UPDATE + `@Modifying` + `@Transactional`):
   ```java
   @Modifying(clearAutomatically = true)
   @Transactional
   @Query("UPDATE Customer c SET c.balance = c.balance + :amount WHERE c.accountNumber = :accountNumber")
   int credit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);
   ```

### 5.2 `BankTransactionRepository.java` (6 Custom Queries)

1. **`findAllLatest`** (JPQL ORDER BY):
   ```java
   @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC, t.id DESC")
   List<BankTransaction> findAllLatest();
   ```
2. **`findHistory`** (JPQL OR Condition):
   ```java
   @Query("SELECT t FROM BankTransaction t WHERE t.fromAccount = :accountNumber OR t.toAccount = :accountNumber ORDER BY t.createdAt DESC, t.id DESC")
   List<BankTransaction> findHistory(@Param("accountNumber") String accountNumber);
   ```
3. **`findByStatusLatest`** (JPQL Enum Filtering):
   ```java
   @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC, t.id DESC")
   List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);
   ```
4. **`countByStatus`** (JPQL Aggregate Count):
   ```java
   @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
   long countByStatus(@Param("status") TransactionStatus status);
   ```
5. **`findLargeTransfers`** (Task 12 Threshold Filtering):
   ```java
   @Query("SELECT t FROM BankTransaction t WHERE t.status = com.example.bankapp.model.TransactionStatus.SUCCESS AND t.amount >= :minAmount ORDER BY t.createdAt DESC, t.id DESC")
   List<BankTransaction> findLargeTransfers(@Param("minAmount") BigDecimal minAmount);
   ```
6. **`getTotalSentByAccount`** (Task 12 Aggregate Sum):
   ```java
   @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BankTransaction t WHERE t.fromAccount = :accountNumber AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS")
   BigDecimal getTotalSentByAccount(@Param("accountNumber") String accountNumber);
   ```

---

## 6. Transaction Management & Rollback Architecture

### 6.1 ACID Properties in the Banking Context
| ACID Property | Real-World Guarantee in Bank System |
|---|---|
| **Atomicity** | All transfer steps (sender debit, optional fee credit, receiver credit, transaction logging) succeed together or fail together. Money never leaves one account without arriving at the intended destination. |
| **Consistency** | The total quantity of money in the bank is conserved ($S_{total} = \sum \text{balances}$). Balances can never become negative. |
| **Isolation** | Concurrent transfer operations run without dirty reads, non-repeatable reads, or phantom reads. Database row-level locks prevent conflicting balance modifications. |
| **Durability** | Once a transfer is committed, the updated balances and transaction rows are written to persistent disk storage (redo log / InnoDB tablespaces), surviving application or OS crashes. |

### 6.2 Spring Dynamic Proxy Execution Flow
When a client invokes `BankService.transfer()`, the request passes through a Spring dynamic proxy:
```
Controller ---> [ Spring Proxy Interceptor ] ---> BankService.transfer()
                     | BEGIN Transaction
                     | debit(sender)      <- UPDATE customers SET balance = balance - amount ...
                     | [Simulated Check]  <- If ticked, throws BankException
                     | credit(receiver)   <- UPDATE customers SET balance = balance + amount ...
                     | save(transaction)  <- INSERT INTO bank_transactions ...
                     |
                     |--> If SUCCESS: COMMIT Transaction (changes written permanently)
                     |--> If EXCEPTION: ROLLBACK Transaction (all balance changes undone)
```

### 6.3 Independent Failure Auditing with `REQUIRES_NEW`
When `BankService.transfer()` throws an exception, Spring immediately marks the current transaction for rollback. If the application attempted to save a `FAILED` record within that same transactional method, the rollback would wipe out the failure log as well!

To solve this:
1. `TransactionAuditService` is placed in a **separate Spring service bean**.
2. Its method `recordFailure()` is annotated with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
3. When called, Spring temporarily suspends the outer rolling-back transaction, opens a fresh, independent physical transaction, inserts the `FAILED` transaction record, and commits it immediately.
4. The outer transaction then completes its rollback without affecting the audit trail.

---

## 7. Compulsory Assignment Questions & Academic Answers

### Question A: What would have happened to the money in test 4 without `@Transactional`?
**Answer:**  
In Test 4, a transfer of LKR 1,000.00 is initiated from Kamal (100001) to Saman (100003) with the "Simulate a system failure" option checked.
Without `@Transactional` (such as under `Propagation.SUPPORTS` or with auto-commit enabled on individual statements):
1. The `debit()` query executes first and commits immediately to MySQL: Kamal's balance drops from LKR 50,000.00 to LKR 49,000.00.
2. The code then encounters the simulated failure and throws a `BankException`.
3. Because no overarching transaction coordinator wraps the steps, the previously committed debit is never undone.
4. Execution halts before reaching the `credit()` operation for Saman. Saman receives nothing (remains at LKR 10,000.00).
5. **Consequence:** The LKR 1,000.00 has completely vanished from the banking system. The bank's total assets drop from LKR 85,000.00 to LKR 84,000.00, violating Atomicity and Consistency. With `@Transactional(rollbackFor = Exception.class)`, Spring detects the unhandled exception, issues a `ROLLBACK` to MySQL InnoDB, and restores Kamal's balance back to LKR 50,000.00.

---

### Question B: Why is `recordFailure` in a separate class with `REQUIRES_NEW`?
**Answer:**  
There are two vital technical reasons:
1. **Transaction Boundary Separation (`REQUIRES_NEW`):** When the transfer fails, the transfer transaction is marked as `rollback-only`. If `recordFailure` participated in the same transaction (`REQUIRED`), any record saved to `bank_transactions` would be discarded during the rollback. `Propagation.REQUIRES_NEW` instructs Spring to suspend the active rolling-back transaction, create a brand-new database transaction, insert the `FAILED` audit record, and commit it independently.
2. **Spring AOP Dynamic Proxy Mechanics (Avoiding Self-Invocation):** Spring manages transactions by wrapping beans in dynamic proxies. When an external class calls a method on a bean, the call goes through the proxy interceptor which initiates the transaction. If a `@Transactional(propagation = Propagation.REQUIRES_NEW)` method is defined in the same `BankService` class and invoked as `this.recordFailure(...)`, the invocation bypasses the Spring proxy entirely (direct local method dispatch). As a result, the `REQUIRES_NEW` annotation is ignored. By placing `recordFailure` in an external service (`TransactionAuditService`), Spring's proxy intercepts the call and starts the independent transaction as intended.

---

### Question C: Why does `debit` use `UPDATE ... WHERE balance >= :amount` instead of reading the balance first?
**Answer:**  
In concurrent environments, the naive two-step approach:
1. `SELECT balance FROM customers WHERE account_number = ?`
2. `if (balance >= amount) UPDATE customers SET balance = balance - amount ...`

suffers from a classic **Race Condition (Time-of-Check to Time-of-Use / TOCTOU)**:
- Suppose Account 100001 has LKR 10,000.00.
- Thread A reads balance = LKR 10,000.00 and decides to transfer LKR 8,000.00.
- Concurrently, before Thread A updates, Thread B reads the same balance = LKR 10,000.00 and decides to withdraw LKR 8,000.00.
- Both threads conclude there are sufficient funds. Thread A deducts LKR 8,000.00 (balance becomes 2,000.00). Thread B then deducts LKR 8,000.00 (balance becomes -6,000.00).
- The account is now overdrawn, and money was duplicated.

By implementing `debit` as a single atomic SQL statement:
```sql
UPDATE customers SET balance = balance - :amount WHERE account_number = :accountNumber AND balance >= :amount;
```
MySQL InnoDB locks the specific row and evaluates the balance condition at the exact moment of update within the database engine. If the balance is insufficient, the `WHERE` condition matches 0 rows. The query returns an update count of `0`, allowing the application to throw an `InsufficientBalanceException` with zero risk of concurrency anomalies.

---

## 8. Verification & Test Execution Results

The application includes automated integration tests (`BankTransferApplicationTests` and `BankControllerTest`) covering all 7 assignment scenarios and Task 12 extensions:

| Test Scenario | Action Performed | Expected Result | Actual Result | Status |
|---|---|---|---|---|
| **Test 1** | Transfer LKR 5,000 from Kamal (100001) to Nimali (100002) | Kamal: 45,000.00; Nimali: 30,000.00; Total: 85,000.00; SUCCESS record logged | Balances updated exactly; SUCCESS transaction saved; Total balance conserved | **PASSED** |
| **Test 2** | Transfer LKR 20,000 from Saman (10,000) to Kamal | InsufficientBalanceException; Balances unchanged; FAILED record logged | Error caught; Balances remain unchanged; FAILED audit entry created | **PASSED** |
| **Test 3** | Same account selected as sender and receiver (100001 -> 100001) | Validation error: "Sender and receiver accounts must be different"; FAILED record logged | Validation triggered; Balances unchanged; FAILED record logged | **PASSED** |
| **Test 4** | Transfer LKR 1,000 Kamal to Saman with "Simulate system failure" ticked | Withdrawal executed, exception triggered, entire transfer rolled back; Kamal remains 50,000; Saman remains 10,000; FAILED record saved | Transaction rolled back; Kamal: 50,000.00; Saman: 10,000.00; FAILED audit record preserved | **PASSED** |
| **Test 5** | Form submission with empty amount or 0 | Bean validation triggers field errors; no database modifications | Validation errors shown under fields; form repopulated safely | **PASSED** |
| **Test 6** | Add customer Nuwan (100004); then re-add same account number | First creation succeeds; duplicate creation rejected with BankException | Customer 100004 created; duplicate attempt safely blocked | **PASSED** |
| **Test 7** | Search keyword "kam" and view account history | Kamal appears in search results; history displays both SUCCESS and FAILED transactions | Filtered table matches; history accurately logs incoming & outgoing transactions | **PASSED** |
| **Task 12.1**| Query large transfers (`/large-transfers?min=10000`) | Returns only successful transfers >= 10,000.00 | Filtered list matches threshold criteria | **PASSED** |
| **Task 12.2**| Total sent calculation (`getTotalSentByAccount`) | Returns sum of all successful transfers sent by account | Exact mathematical sum computed via JPQL aggregate | **PASSED** |
| **Task 12.3**| Deposit & Withdrawal endpoints | Credit and debit accounts with atomic balance updates and audit logs | Deposits increase balance; withdrawals debit safely with zero-balance prevention | **PASSED** |
| **Task 12.4**| 1% Transfer Fee atomically credited to bank account `999999` | Sender debited amount + 1%; Receiver credited amount; Account 999999 credited 1% fee | All 3 updates execute in single transaction; total money conserved | **PASSED** |

---

## 9. Task 10: Comparative Experiment (With vs Without `@Transactional`)

### 9.1 The Unsafe Demonstration (`Propagation.SUPPORTS`)
1. In `BankService.java`, the annotation on `transfer()` is temporarily changed:
   ```java
   // @Transactional(rollbackFor = Exception.class)
   @Transactional(propagation = Propagation.SUPPORTS)
   public BankTransaction transfer(TransferRequest request) { ... }
   ```
2. When a transfer of LKR 1,000.00 from Kamal to Saman is executed with "Simulate a system failure" enabled:
   - **Sender (Kamal):** Balance drops from LKR 50,000.00 to LKR 49,000.00 (debit committed).
   - **Simulated Crash:** Exception thrown after debit.
   - **Receiver (Saman):** Never receives the funds (remains at LKR 10,000.00).
   - **Total Bank Balance:** Decreases from LKR 85,000.00 to LKR 84,000.00.
   - **Observation:** The money vanished from existence because the auto-committed withdrawal was not reversed!

### 9.2 Restoration & SQL Data Repair
To restore transactional safety, `BankService.java` is restored to:
```java
@Transactional(rollbackFor = Exception.class)
public BankTransaction transfer(TransferRequest request) { ... }
```
When repeated with rollback enabled, the transaction intercepts the failure and executes `ROLLBACK`. Kamal's balance remains unchanged at LKR 50,000.00 and Saman remains at LKR 10,000.00.

The database repair query provided in the assignment for manual restoration in MySQL:
```sql
USE bank_db;
UPDATE customers SET balance = balance + 1000.00 WHERE account_number = '100001';
```

---

## 10. Database Verification Queries (phpMyAdmin / MySQL Terminal)
```sql
USE bank_db;

-- 1. Inspect all customers and current balances
SELECT id, full_name, account_number, email, balance FROM customers;

-- 2. Inspect all logged transactions in descending order of creation
SELECT id, from_account, to_account, amount, status, description, created_at 
FROM bank_transactions 
ORDER BY id DESC;

-- 3. Verify total bank balance conservation
SELECT SUM(balance) AS total_bank_balance FROM customers;
```

---

## 11. GitHub Development Workflow & Pull Request Details
- **Repository Name:** `bank-transfer-app`
- **Remote Origin:** `https://github.com/Thisaru2001/bank-transfer-app.git`
- **Main Branch:** `main` (initial clean starter project commit)
- **Feature Branch:** `feature/bank-transfer` (all implementation commits)
- **Pull Request Title:** `Add bank transfer with @Transactional and @Query`
- **Pull Request Description:**
  - Implemented complete Spring Boot bank transfer application.
  - Implemented custom JPQL, native SQL, and atomic update `@Query` methods.
  - Implemented transactional transfer with ACID compliance and full rollback support.
  - Implemented independent failed transaction auditing with `REQUIRES_NEW`.
  - Implemented cash deposit and withdrawal operations.
  - Implemented large transfer filtering (`/large-transfers?min=10000`) and total sent calculation.
  - Implemented atomic 1% bank fee routed to account `999999`.
  - Verified with comprehensive automated integration tests.
  - Included assignment documentation and laboratory report.

---

## 12. Conclusion
The Bank Transfer System successfully demonstrates the fundamental principles of transaction processing in enterprise software systems. By combining Spring Boot's declarative `@Transactional` management, dynamic proxy routing, atomic conditional repository queries, and isolated auditing propagation, the application guarantees 100% financial consistency, eliminates race conditions, and maintains a resilient audit trail even during system crashes.
