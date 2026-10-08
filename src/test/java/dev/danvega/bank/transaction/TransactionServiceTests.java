package dev.danvega.bank.transaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionServiceTests {

    private final TransactionService service = new TransactionService();

    @Test
    void findsTheAcmeCoffeeDoubleCharge() {
        DuplicateCharges result = service.findDuplicateCharges("C-100");

        assertThat(result.duplicates()).singleElement().satisfies(duplicate -> {
            assertThat(duplicate.merchant()).isEqualTo("Acme Coffee");
            assertThat(duplicate.transactionIds()).containsExactly("T-1005", "T-1006");
        });
    }

    @Test
    void otherCustomersHaveNoDuplicates() {
        assertThat(service.findDuplicateCharges("C-200").duplicates()).isEmpty();
    }

    @Test
    void recentTransactionsAreTheCustomersOwnNewestFirst() {
        TransactionHistory history = service.recentTransactions("C-100");

        assertThat(history.transactions()).allMatch(t -> t.customerId().equals("C-100"));
        assertThat(history.transactions().getFirst().id()).isEqualTo("T-1008");
    }

    @Test
    void cannotFindAnotherCustomersTransaction() {
        assertThat(service.findById("C-200", "T-1006")).isEmpty();
    }

}
