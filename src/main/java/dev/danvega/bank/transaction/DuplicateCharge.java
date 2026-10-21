package dev.danvega.bank.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DuplicateCharge(String merchant, BigDecimal amount, LocalDate date, List<String> transactionIds) {
}
