package dev.danvega.bank.email;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.danvega.bank.dispute.ReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;

// Spring AI reads Swagger's @Schema. nullable = true means "required, but may be null",
// which OpenAI's strict mode needs. Plain @Nullable makes OpenAI reject the schema.
public record DisputeCase(
        @Schema(nullable = true, description = "Last four digits of the card") String cardLastFour,
        @Schema(nullable = true) String merchant,
        @Schema(nullable = true) BigDecimal amount,
        @Schema(nullable = true) LocalDate transactionDate,
        ReasonCode reason,
        @Schema(description = "One or two sentences for the dispute team") String summary,
        @Schema(description = "Names of the fields above that the email does not give") List<String> missingFields) {
}
