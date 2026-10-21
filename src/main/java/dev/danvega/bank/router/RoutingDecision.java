package dev.danvega.bank.router;

import org.springaicommunity.typesafe.judge.JevConfidenceGate.Decision;

public record RoutingDecision(ModelTier tier, String model, double confidence, Decision gate) {
}
