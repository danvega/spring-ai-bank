package dev.danvega.bank.dispute;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dev.danvega.bank.BankData;
import dev.danvega.bank.transaction.Transaction;
import dev.danvega.bank.transaction.TransactionService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class DisputeService {

    private final Map<String, Dispute> disputes = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1005);
    private final TransactionService transactionService;

    public DisputeService(TransactionService transactionService) {
        this.transactionService = transactionService;
        BankData.disputes().forEach(d -> disputes.put(d.id(), d));
    }

    @McpTool(name = "proposeDispute",
            description = "Prepare a dispute. It stays pending until the customer confirms it in the app.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public Dispute propose(
            @McpToolParam(description = "Customer ID, like C-100") String customerId,
            @McpToolParam(description = "Transaction ID, like T-1006") String transactionId,
            ReasonCode reason,
            @McpToolParam(description = "The customer's reason, in one sentence") String explanation) {
        Transaction transaction = transactionService.findById(customerId, transactionId)
                .orElseThrow(() -> new IllegalArgumentException("No transaction " + transactionId + " for this customer"));

        Optional<Dispute> existing = findByTransaction(transactionId);
        if (existing.isPresent()) {
            return existing.get();
        }

        var dispute = new Dispute("D-" + nextId.getAndIncrement(), customerId, transaction.id(),
                transaction.merchant(), transaction.amount(), reason, explanation,
                DisputeStatus.PENDING_CONFIRMATION, LocalDate.now());
        disputes.put(dispute.id(), dispute);
        return dispute;
    }

    public Dispute confirm(String customerId, String disputeId) {
        Dispute dispute = findForCustomer(customerId, disputeId);
        if (dispute.status() != DisputeStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Dispute " + disputeId + " is already " + dispute.status());
        }
        Dispute opened = dispute.withStatus(DisputeStatus.OPEN);
        disputes.put(opened.id(), opened);
        return opened;
    }

    public DisputeList disputesFor(String customerId) {
        return new DisputeList(sorted(disputes.values().stream()
                .filter(d -> d.customerId().equals(customerId))
                .toList()));
    }

    @McpTool(description = "Find disputes by status, at or above a minimum amount in dollars",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public DisputeList findDisputes(DisputeStatus status,
            @McpToolParam(description = "Minimum amount in dollars, like 500") BigDecimal minAmount) {
        return new DisputeList(sorted(disputes.values().stream()
                .filter(d -> d.status() == status)
                .filter(d -> d.amount().compareTo(minAmount) >= 0)
                .toList()));
    }

    private Dispute findForCustomer(String customerId, String disputeId) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute == null || !dispute.customerId().equals(customerId)) {
            throw new DisputeNotFoundException(disputeId);
        }
        return dispute;
    }

    private Optional<Dispute> findByTransaction(String transactionId) {
        return disputes.values().stream()
                .filter(d -> d.transactionId().equals(transactionId))
                .findFirst();
    }

    private List<Dispute> sorted(List<Dispute> list) {
        return list.stream().sorted(Comparator.comparing(Dispute::id)).toList();
    }

}
