package dev.danvega.bank.transaction;

import java.util.List;

public record DuplicateCharges(String customerId, List<DuplicateCharge> duplicates) {
}
