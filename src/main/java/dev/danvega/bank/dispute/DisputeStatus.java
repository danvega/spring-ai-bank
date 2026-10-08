package dev.danvega.bank.dispute;

public enum DisputeStatus {
    /** Proposed, but the customer has not confirmed it yet. */
    PENDING_CONFIRMATION,
    OPEN,
    RESOLVED
}
