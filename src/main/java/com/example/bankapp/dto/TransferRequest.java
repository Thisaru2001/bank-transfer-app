package com.example.bankapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Holds the form data typed into the "Transfer Money" form.
 */
@Getter
@Setter
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Please select sender account")
    private String fromAccount;

    @NotBlank(message = "Please select receiver account")
    private String toAccount;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 12, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;

    private boolean simulateFailure;

    private boolean applyFee;

    public TransferRequest(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.simulateFailure = simulateFailure;
        this.applyFee = false;
    }

    public TransferRequest(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure, boolean applyFee) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.simulateFailure = simulateFailure;
        this.applyFee = applyFee;
    }
}
