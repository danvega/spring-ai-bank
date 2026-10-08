package dev.danvega.bank.assistant;

import dev.danvega.bank.dispute.Dispute;
import dev.danvega.bank.dispute.DisputeService;
import dev.danvega.bank.dispute.ReasonCode;
import dev.danvega.bank.transaction.DuplicateCharges;
import dev.danvega.bank.transaction.TransactionHistory;
import dev.danvega.bank.transaction.TransactionService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The tools for one signed-in customer. The customer ID is set here, not by the model,
 * so the model can't look at anyone else's account.
 * There is no tool to confirm a dispute. Only the customer can do that.
 */
class CustomerTools {

    private final String customerId;
    private final TransactionService transactionService;
    private final DisputeService disputeService;

    CustomerTools(String customerId, TransactionService transactionService, DisputeService disputeService) {
        this.customerId = customerId;
        this.transactionService = transactionService;
        this.disputeService = disputeService;
    }

    @Tool(description = "List the customer's recent card transactions, newest first")
    TransactionHistory recentTransactions() {
        return transactionService.recentTransactions(customerId);
    }

    @Tool(description = "Find charges with the same merchant, amount and date")
    DuplicateCharges findDuplicateCharges() {
        return transactionService.findDuplicateCharges(customerId);
    }

    @Tool(description = """
            Prepare a dispute for one transaction. This does not open it.
            The customer must confirm it in the app before it opens.""")
    Dispute proposeDispute(
            @ToolParam(description = "Transaction ID, like T-1006") String transactionId,
            ReasonCode reason,
            @ToolParam(description = "The customer's reason, in one sentence") String explanation) {
        return disputeService.propose(customerId, transactionId, reason, explanation);
    }

}
