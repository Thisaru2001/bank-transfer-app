package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.Customer;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;

    private void populateDashboardModel(Model model, String keyword) {
        model.addAttribute("totalCustomers", bankService.countCustomers());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        model.addAttribute("successCount", bankService.countSuccessfulTransfers());
        model.addAttribute("failedCount", bankService.countFailedTransfers());
        model.addAttribute("allCustomers", bankService.getAllCustomers());
        model.addAttribute("customers", bankService.searchCustomers(keyword));
        model.addAttribute("transactions", bankService.getLatestTransactions());
        model.addAttribute("keyword", keyword);
    }

    /**
     * Dashboard view showing statistics, transfer form, customer addition, customer table,
     * and latest transactions. Supports optional search by customer name.
     */
    @GetMapping("/")
    public String dashboard(@RequestParam(value = "keyword", required = false) String keyword, Model model) {
        populateDashboardModel(model, keyword);
        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }
        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", new Customer());
        }
        return "bank";
    }

    /**
     * Adds a new customer with validation checks against duplicate accounts and emails.
     */
    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("customer") Customer customer,
                              BindingResult result,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        if (result.hasErrors()) {
            populateDashboardModel(model, null);
            if (!model.containsAttribute("transferRequest")) {
                model.addAttribute("transferRequest", new TransferRequest());
            }
            return "bank";
        }

        try {
            bankService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("success",
                    "Customer " + customer.getFullName() + " (Account: " + customer.getAccountNumber() + ") created successfully.");
        } catch (BankException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * Executes bank transfer. If any failure occurs, the main transaction is rolled back,
     * and a FAILED audit entry is saved independently via REQUIRES_NEW.
     */
    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferRequest") TransferRequest request,
                           BindingResult result,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (result.hasErrors()) {
            populateDashboardModel(model, null);
            if (!model.containsAttribute("customer")) {
                model.addAttribute("customer", new Customer());
            }
            return "bank";
        }

        try {
            bankService.transfer(request);
            redirectAttributes.addFlashAttribute("success",
                    "Transfer of LKR " + request.getAmount() + " from " + request.getFromAccount()
                            + " to " + request.getToAccount() + " completed successfully.");
        } catch (Exception e) {
            // Independent audit log with REQUIRES_NEW
            auditService.recordFailure(request.getFromAccount(), request.getToAccount(), request.getAmount(), e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * Displays transaction history for a specific customer account.
     */
    @GetMapping("/accounts/{accountNumber}/history")
    public String accountHistory(@PathVariable("accountNumber") String accountNumber, Model model) {
        Customer customer = bankService.getCustomerByAccount(accountNumber);
        model.addAttribute("customer", customer);
        model.addAttribute("transactions", bankService.getAccountHistory(accountNumber));
        model.addAttribute("totalSent", bankService.getTotalSent(accountNumber));
        return "history";
    }

    /**
     * Task 12 Extension: Filter and view large transfers above a given amount.
     */
    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(value = "min", defaultValue = "10000.00") BigDecimal min, Model model) {
        model.addAttribute("transfers", bankService.getLargeTransfers(min));
        model.addAttribute("min", min);
        return "large-transfers";
    }

    /**
     * Task 12 Extension: Deposit view and operation.
     */
    @GetMapping("/deposit")
    public String showDepositForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers());
        return "deposit";
    }

    @PostMapping("/deposit")
    public String processDeposit(@RequestParam("accountNumber") String accountNumber,
                                 @RequestParam("amount") BigDecimal amount,
                                 RedirectAttributes redirectAttributes) {
        try {
            bankService.deposit(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success",
                    "Deposit of LKR " + amount + " to account " + accountNumber + " completed successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/deposit";
    }

    /**
     * Task 12 Extension: Withdrawal view and operation.
     */
    @GetMapping("/withdraw")
    public String showWithdrawalForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers());
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String processWithdrawal(@RequestParam("accountNumber") String accountNumber,
                                    @RequestParam("amount") BigDecimal amount,
                                    RedirectAttributes redirectAttributes) {
        try {
            bankService.withdraw(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success",
                    "Withdrawal of LKR " + amount + " from account " + accountNumber + " completed successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/withdraw";
    }
}
