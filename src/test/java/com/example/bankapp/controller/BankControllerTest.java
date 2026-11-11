package com.example.bankapp.controller;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class BankControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private BankController bankController;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(bankController).build();

        transactionRepository.deleteAll();
        customerRepository.deleteAll();

        customerRepository.save(new Customer("Kamal", "100001", "kamal@example.com", new BigDecimal("50000.00")));
        customerRepository.save(new Customer("Nimali", "100002", "nimali@example.com", new BigDecimal("25000.00")));
        customerRepository.save(new Customer("Saman", "100003", "saman@example.com", new BigDecimal("10000.00")));
        customerRepository.save(new Customer("Bank Central Fee Account", "999999", "bank@example.com", new BigDecimal("0.00")));
    }

    @Test
    @DisplayName("GET / returns dashboard view with required attributes")
    void testDashboardView() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("bank"))
                .andExpect(model().attributeExists("totalCustomers", "totalBalance", "allCustomers", "transactions"));
    }

    @Test
    @DisplayName("GET / with keyword searches customers")
    void testDashboardSearch() throws Exception {
        mockMvc.perform(get("/").param("keyword", "Nimali"))
                .andExpect(status().isOk())
                .andExpect(view().name("bank"))
                .andExpect(model().attribute("customers", hasSize(1)));
    }

    @Test
    @DisplayName("POST /transfer executes transfer and redirects with success flash message")
    void testSuccessfulTransferEndpoint() throws Exception {
        mockMvc.perform(post("/transfer")
                        .param("fromAccount", "100001")
                        .param("toAccount", "100002")
                        .param("amount", "5000.00")
                        .param("simulateFailure", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("POST /transfer with simulation failure redirects with error message and rolls back")
    void testSimulatedFailureTransferEndpoint() throws Exception {
        mockMvc.perform(post("/transfer")
                        .param("fromAccount", "100001")
                        .param("toAccount", "100002")
                        .param("amount", "1000.00")
                        .param("simulateFailure", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("GET /accounts/{no}/history displays account history view")
    void testAccountHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/accounts/100001/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attributeExists("customer", "transactions", "totalSent"));
    }

    @Test
    @DisplayName("GET /large-transfers displays large transfers view")
    void testLargeTransfersEndpoint() throws Exception {
        mockMvc.perform(get("/large-transfers").param("min", "10000"))
                .andExpect(status().isOk())
                .andExpect(view().name("large-transfers"))
                .andExpect(model().attributeExists("transfers", "min"));
    }

    @Test
    @DisplayName("GET & POST /deposit deposit money and redirect")
    void testDepositEndpoint() throws Exception {
        mockMvc.perform(get("/deposit"))
                .andExpect(status().isOk())
                .andExpect(view().name("deposit"));

        mockMvc.perform(post("/deposit")
                        .param("accountNumber", "100001")
                        .param("amount", "2500.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("GET & POST /withdraw withdraw money and redirect")
    void testWithdrawEndpoint() throws Exception {
        mockMvc.perform(get("/withdraw"))
                .andExpect(status().isOk())
                .andExpect(view().name("withdraw"));

        mockMvc.perform(post("/withdraw")
                        .param("accountNumber", "100001")
                        .param("amount", "1500.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/withdraw"))
                .andExpect(flash().attributeExists("success"));
    }
}
