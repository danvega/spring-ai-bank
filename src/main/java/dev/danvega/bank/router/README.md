# Route to the right model with a decision model

LLMs write. Decision models decide. This demo asks Jev, a decision model, which GPT-6 model should answer each customer question. Then that model writes the answer.

A card FAQ goes to the cheapest model. A mortgage hardship question goes to the strongest one. The bank pays for the big model only when it matters.

## The code

- [ModelTier.java](ModelTier.java): three tiers, cheapest first. LUNA (`gpt-6-luna`), SOL (`gpt-6.1-sol`) and ASTRA (`gpt-6-astra`). The descriptions are the routing rules. To change the routing, change the English, not the code.
- [ModelRouter.java](ModelRouter.java): one Jev call with a `Choice` question, one option per tier. The answer is a tier, a probability for each tier, and a confidence. Nothing to parse.
- A confidence gate decides what happens when Jev isn't sure:

```java
static final JevConfidenceGate GATE = JevConfidenceGate.builder()
        .floor(0.60)            // below this, ASTRA answers
        .require("LUNA", 0.80)  // the cheap model must be a clear call
        .build();
```

  `EXECUTE` uses Jev's pick. `CONFIRM` goes one tier up. `ESCALATE` goes to ASTRA.
- [RouterController.java](RouterController.java): `POST /api/router/route` returns only the decision. `POST /api/router/chat` routes, then answers with the chosen model, set per request with `OpenAiResponsesChatOptions`.

## Try it

You need `TYPESAFE_API_KEY` as well as `OPENAI_API_KEY`. Run R1 to R5 in [http/5-router.http](../../../../../../../http/5-router.http):

- **R1** "How do I freeze my debit card?": LUNA. No LLM was called, only one Jev call.
- **R2** A duplicate charge: SOL.
- **R3** A mortgage hardship question: ASTRA.
- **R4** Route and answer. The log line names the Jev model version, the request ID and the pick. That's the audit trail.
- **R5** An unclear question. If Jev isn't sure, the gate picks a stronger tier and `reason` says why.

## Good to know

- **Spring AI TypeSafe is a Spring AI Community project** (`spring-ai-starter-typesafe` 0.4.0), not core Spring AI. `JevConfidenceGate` is in `typesafe-spring-ai`.
- **The model is pinned** to `jev-1.13.0` in `application.yaml`, because `jev-latest` moves.
- **To keep data in your network,** point `spring.ai.typesafe.base-url` at a local Ollama (`model: nimble`) or a Laya server. Measure the thresholds again on the model you run.
