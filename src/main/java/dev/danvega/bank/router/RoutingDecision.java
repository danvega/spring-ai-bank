package dev.danvega.bank.router;

import java.util.Map;

import org.springaicommunity.typesafe.judge.JevConfidenceGate.Decision;

public record RoutingDecision(ModelTier tier, String model, double confidence, Map<String, Double> probabilities,
        ModelTier jevPick, Decision gate, String reason) {
}
