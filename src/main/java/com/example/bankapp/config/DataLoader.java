package com.example.bankapp.config;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inserts sample customers the first time the application starts (when the database is empty).
 * Initial customers:
 *  - Kamal (100001)  : LKR 50,000.00
 *  - Nimali (100002) : LKR 25,000.00
 *  - Saman (100003)  : LKR 10,000.00
 *  - Bank Central Fee Account (999999): LKR 0.00 (Task 12 Extension)
 * Initial total money: LKR 85,000.00
 */
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) throws Exception {
        if (customerRepository.count() == 0) {
            Customer c1 = new Customer("Kamal", "100001", "kamal@example.com", new BigDecimal("50000.00"));
            Customer c2 = new Customer("Nimali", "100002", "nimali@example.com", new BigDecimal("25000.00"));
            Customer c3 = new Customer("Saman", "100003", "saman@example.com", new BigDecimal("10000.00"));
            Customer cBank = new Customer("Bank Central Fee Account", "999999", "bank@example.com", new BigDecimal("0.00"));

            customerRepository.saveAll(List.of(c1, c2, c3, cBank));
        }
    }
}
