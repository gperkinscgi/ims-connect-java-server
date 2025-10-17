package com.cgi.icbc.imsconnect.examples.service;

import com.cgi.icbc.imsconnect.service.model.AccountBalance;
import com.cgi.icbc.imsconnect.service.model.TransferResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example account service implementation for demonstration purposes.
 * This is a simple in-memory implementation that should NOT be used in production.
 *
 * NOTE: This is example code for demonstration only. Production implementations
 * should integrate with real banking systems, databases, and proper security.
 */
@Service
public class AccountService {

    // In-memory account storage - NOT for production use
    private final Map<String, AccountBalance> accounts = new ConcurrentHashMap<>();

    public AccountService() {
        // Initialize with some example accounts
        accounts.put("123456789", new AccountBalance("123456789", 150000, "USD", "A")); // $1,500.00
        accounts.put("987654321", new AccountBalance("987654321", 250000, "USD", "A")); // $2,500.00
        accounts.put("555666777", new AccountBalance("555666777", 75000, "CAD", "A"));  // $750.00 CAD
        accounts.put("111222333", new AccountBalance("111222333", 0, "USD", "C"));      // Closed account
    }

    /**
     * Get account balance for the specified account number.
     *
     * @param accountNumber the account number to look up
     * @return the account balance information
     * @throws IllegalArgumentException if account not found
     */
    public AccountBalance getAccountBalance(String accountNumber) {
        AccountBalance balance = accounts.get(accountNumber);
        if (balance == null) {
            throw new IllegalArgumentException("Account not found: " + accountNumber);
        }
        return balance;
    }

    /**
     * Transfer funds between accounts.
     *
     * @param fromAccount source account number
     * @param toAccount destination account number
     * @param amountCents amount in cents to transfer
     * @return transfer result
     * @throws IllegalArgumentException if accounts not found or invalid transfer
     */
    public TransferResult transferFunds(String fromAccount, String toAccount, long amountCents) {
        AccountBalance fromBalance = accounts.get(fromAccount);
        AccountBalance toBalance = accounts.get(toAccount);

        if (fromBalance == null) {
            throw new IllegalArgumentException("Source account not found: " + fromAccount);
        }
        if (toBalance == null) {
            throw new IllegalArgumentException("Destination account not found: " + toAccount);
        }

        if (!"A".equals(fromBalance.getAccountStatus())) {
            throw new IllegalArgumentException("Source account is not active: " + fromAccount);
        }
        if (!"A".equals(toBalance.getAccountStatus())) {
            throw new IllegalArgumentException("Destination account is not active: " + toAccount);
        }

        if (fromBalance.getAmountCents() < amountCents) {
            throw new IllegalArgumentException("Insufficient funds in account: " + fromAccount);
        }

        if (amountCents <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        // Perform the transfer
        long newFromBalance = fromBalance.getAmountCents() - amountCents;
        long newToBalance = toBalance.getAmountCents() + amountCents;

        accounts.put(fromAccount, new AccountBalance(fromAccount, newFromBalance,
            fromBalance.getCurrencyCode(), fromBalance.getAccountStatus()));
        accounts.put(toAccount, new AccountBalance(toAccount, newToBalance,
            toBalance.getCurrencyCode(), toBalance.getAccountStatus()));

        return new TransferResult(fromAccount, toAccount, amountCents, "SUCCESS", null);
    }

    /**
     * Deposit funds to an account.
     *
     * @param accountNumber account to deposit to
     * @param amountCents amount in cents to deposit
     * @return updated account balance
     * @throws IllegalArgumentException if account not found or invalid deposit
     */
    public AccountBalance deposit(String accountNumber, long amountCents) {
        AccountBalance balance = accounts.get(accountNumber);
        if (balance == null) {
            throw new IllegalArgumentException("Account not found: " + accountNumber);
        }

        if (!"A".equals(balance.getAccountStatus())) {
            throw new IllegalArgumentException("Account is not active: " + accountNumber);
        }

        if (amountCents <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        long newBalance = balance.getAmountCents() + amountCents;
        AccountBalance updatedBalance = new AccountBalance(accountNumber, newBalance,
            balance.getCurrencyCode(), balance.getAccountStatus());

        accounts.put(accountNumber, updatedBalance);
        return updatedBalance;
    }

    /**
     * Withdraw funds from an account.
     *
     * @param accountNumber account to withdraw from
     * @param amountCents amount in cents to withdraw
     * @return updated account balance
     * @throws IllegalArgumentException if account not found, insufficient funds, or invalid withdrawal
     */
    public AccountBalance withdraw(String accountNumber, long amountCents) {
        AccountBalance balance = accounts.get(accountNumber);
        if (balance == null) {
            throw new IllegalArgumentException("Account not found: " + accountNumber);
        }

        if (!"A".equals(balance.getAccountStatus())) {
            throw new IllegalArgumentException("Account is not active: " + accountNumber);
        }

        if (amountCents <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        if (balance.getAmountCents() < amountCents) {
            throw new IllegalArgumentException("Insufficient funds in account: " + accountNumber);
        }

        long newBalance = balance.getAmountCents() - amountCents;
        AccountBalance updatedBalance = new AccountBalance(accountNumber, newBalance,
            balance.getCurrencyCode(), balance.getAccountStatus());

        accounts.put(accountNumber, updatedBalance);
        return updatedBalance;
    }

    /**
     * Check if an account exists and is active.
     *
     * @param accountNumber account number to check
     * @return true if account exists and is active
     */
    public boolean isAccountActive(String accountNumber) {
        AccountBalance balance = accounts.get(accountNumber);
        return balance != null && "A".equals(balance.getAccountStatus());
    }
}