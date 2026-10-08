package dev.danvega.bank.email;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.danvega.bank.dispute.ReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the dispute team needs from a customer's email.
 * OpenAI strict mode wants every field present, so a missing value is null (not left out).
 */
public record DisputeCase(
        @Schema(nullable = true, description = "Last four digits of the card") String cardLastFour,
        @Schema(nullable = true) String merchant,
        @Schema(nullable = true) BigDecimal amount,
        @Schema(nullable = true) LocalDate transactionDate,
        ReasonCode reason,
        @Schema(description = "One or two sentences for the dispute team") String summary,
        @Schema(description = "Names of the fields above that the email does not give") List<String> missingFields) {
}
