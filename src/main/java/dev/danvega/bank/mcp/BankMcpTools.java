package dev.danvega.bank.mcp;

import java.math.BigDecimal;

import dev.danvega.bank.dispute.Dispute;
import dev.danvega.bank.dispute.DisputeList;
import dev.danvega.bank.dispute.DisputeService;
import dev.danvega.bank.dispute.DisputeStatus;
import dev.danvega.bank.dispute.ReasonCode;
import dev.danvega.bank.transaction.DuplicateCharges;
import dev.danvega.bank.transaction.TransactionHistory;
import dev.danvega.bank.transaction.TransactionService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

// Tools designed for a back-office agent: a small surface on top of the services. No tool confirms a dispute.
@Component
public class BankMcpTools {

    private final TransactionService transactionService;
    private final DisputeService disputeService;

    public BankMcpTools(TransactionService transactionService, DisputeService disputeService) {
        this.transactionService = transactionService;
        this.disputeService = disputeService;
    }

    @McpTool(description = "Find disputes by status, at or above an amount in dollars. Use it to triage the dispute queue.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public DisputeList findDisputes(DisputeStatus status,
            @McpToolParam(description = "Minimum amount in dollars, like 500") BigDecimal minAmount) {
        return disputeService.findDisputes(status, minAmount);
    }

    @McpTool(description = "List a customer's recent card transactions, newest first",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public TransactionHistory recentTransactions(
            @McpToolParam(description = "Customer ID, like C-100") String customerId) {
        return transactionService.recentTransactions(customerId);
    }

    @McpTool(description = "Find a customer's charges with the same merchant, amount and date",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public DuplicateCharges findDuplicateCharges(
            @McpToolParam(description = "Customer ID, like C-100") String customerId) {
        return transactionService.findDuplicateCharges(customerId);
    }

    @McpTool(description = "Prepare a dispute for one transaction. It stays pending until the customer confirms it in the app.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public Dispute proposeDispute(
            @McpToolParam(description = "Customer ID, like C-100") String customerId,
            @McpToolParam(description = "Transaction ID, like T-1006") String transactionId,
            ReasonCode reason,
            @McpToolParam(description = "The customer's reason, in one sentence") String explanation) {
        return disputeService.propose(customerId, transactionId, reason, explanation);
    }

}
