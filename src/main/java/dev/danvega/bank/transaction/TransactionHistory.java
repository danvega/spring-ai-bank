package dev.danvega.bank.transaction;

import java.util.List;

public record TransactionHistory(String customerId, List<Transaction> transactions) {
}
