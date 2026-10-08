package dev.danvega.bank.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Two or more charges with the same merchant, amount and date. */
public record DuplicateCharge(String merchant, BigDecimal amount, LocalDate date, List<String> transactionIds) {
}
