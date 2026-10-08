package dev.danvega.bank.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.danvega.bank.BankData;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

    private final List<Transaction> transactions = BankData.transactions();

    @McpTool(description = "List a customer's recent card transactions, newest first",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public TransactionHistory recentTransactions(
            @McpToolParam(description = "Customer ID, like C-100") String customerId) {
        List<Transaction> recent = transactions.stream()
                .filter(t -> t.customerId().equals(customerId))
                .sorted(Comparator.comparing(Transaction::date).reversed())
                .limit(20)
                .toList();
        return new TransactionHistory(customerId, recent);
    }

    @McpTool(description = "Find charges with the same merchant, amount and date",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public DuplicateCharges findDuplicateCharges(
            @McpToolParam(description = "Customer ID, like C-100") String customerId) {
        Map<ChargeKey, List<Transaction>> charges = new LinkedHashMap<>();
        for (Transaction t : recentTransactions(customerId).transactions()) {
            var key = new ChargeKey(t.merchant(), t.amount(), t.date());
            charges.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<DuplicateCharge> duplicates = charges.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> toDuplicate(entry.getKey(), entry.getValue()))
                .toList();
        return new DuplicateCharges(customerId, duplicates);
    }

    public Optional<Transaction> findById(String customerId, String transactionId) {
        return transactions.stream()
                .filter(t -> t.customerId().equals(customerId))
                .filter(t -> t.id().equals(transactionId))
                .findFirst();
    }

    private DuplicateCharge toDuplicate(ChargeKey key, List<Transaction> charges) {
        List<String> ids = charges.stream().map(Transaction::id).toList();
        return new DuplicateCharge(key.merchant(), key.amount(), key.date(), ids);
    }

    private record ChargeKey(String merchant, BigDecimal amount, LocalDate date) {
    }

}
