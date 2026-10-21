package dev.danvega.bank.router;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.judge.JevConfidenceGate;
import org.springaicommunity.typesafe.judge.JevConfidenceGate.Decision;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import org.springframework.stereotype.Service;

@Service
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    static final String QUESTION = "tier";

    // Tuned for Jev. Measure again for a local model.
    static final JevConfidenceGate GATE = JevConfidenceGate.builder()
            .floor(0.60)
            .require("LUNA", 0.80)
            .build();

    private final TypeSafeClient jev;
    private final Choice tierChoice;

    public ModelRouter(TypeSafeClient jev) {
        this.jev = jev;
        Choice.Builder choice = Choice.builder().instructions("""
                Which model should answer this bank customer's message? \
                Pick the smallest one that can answer it well and safely. \
                When money, debt or the customer's wellbeing is at stake, pick the stronger model.""");
        for (ModelTier tier : ModelTier.values()) {
            choice.option(tier.name(), tier.description());
        }
        this.tierChoice = choice.build();
    }

    public RoutingDecision route(String message) {
        SystemOneResponse response = jev.systemOne(SystemOneRequest.builder()
                .state(message)
                .question(QUESTION, tierChoice)
                .build());
        ChoiceAnswer answer = response.choice(QUESTION);

        ModelTier pick = ModelTier.valueOf(answer.value());
        Decision gate = GATE.decide(answer);
        ModelTier tier = switch (gate) {
            case EXECUTE -> pick;
            case CONFIRM -> pick.stronger();
            case ESCALATE -> ModelTier.ASTRA;
        };

        log.info("Jev {} (request {}) picked {} at {}. Gate: {}. {} answers.",
                response.model(), response.requestId(), pick, answer.confidence(), gate, tier);
        return new RoutingDecision(tier, tier.modelId(), answer.confidence(), answer.probabilities(),
                pick, gate, reason(pick, answer.confidence(), gate, tier));
    }

    private static String reason(ModelTier pick, double confidence, Decision gate, ModelTier tier) {
        String why = switch (gate) {
            case EXECUTE -> "Sure enough";
            case CONFIRM -> String.format(Locale.ROOT, "%s needs %.2f", pick, GATE.requiredFor(pick.name()));
            case ESCALATE -> String.format(Locale.ROOT, "Below the %.2f floor", GATE.floor());
        };
        return String.format(Locale.ROOT, "Jev picked %s at %.2f. %s, so %s answers.", pick, confidence, why, tier);
    }

}
