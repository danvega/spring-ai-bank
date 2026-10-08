package dev.danvega.bank.dispute;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Dispute(
        String id,
        String customerId,
        String transactionId,
        String merchant,
        BigDecimal amount,
        ReasonCode reason,
        String explanation,
        DisputeStatus status,
        LocalDate createdOn) {

    public Dispute withStatus(DisputeStatus newStatus) {
        return new Dispute(id, customerId, transactionId, merchant, amount, reason, explanation, newStatus, createdOn);
    }

}
