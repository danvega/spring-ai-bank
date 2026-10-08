package dev.danvega.bank.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(
        String id,
        String customerId,
        String cardLastFour,
        String merchant,
        BigDecimal amount,
        LocalDate date) {
}
