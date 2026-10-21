package dev.danvega.bank.dispute;

import java.math.BigDecimal;

import dev.danvega.bank.transaction.TransactionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisputeServiceTests {

    private final DisputeService service = new DisputeService(new TransactionService());

    @Test
    void proposingCreatesAPendingDispute() {
        Dispute dispute = service.propose("C-100", "T-1006", ReasonCode.DUPLICATE_CHARGE, "Charged twice.");

        assertThat(dispute.id()).isEqualTo("D-1005");
        assertThat(dispute.status()).isEqualTo(DisputeStatus.PENDING_CONFIRMATION);
        assertThat(dispute.amount()).isEqualByComparingTo("18.75");
        assertThat(service.disputesFor("C-100").disputes()).contains(dispute);
    }

    @Test
    void confirmingOpensIt() {
        Dispute proposed = service.propose("C-100", "T-1006", ReasonCode.DUPLICATE_CHARGE, "Charged twice.");

        Dispute opened = service.confirm("C-100", proposed.id());

        assertThat(opened.status()).isEqualTo(DisputeStatus.OPEN);
        assertThat(service.disputesFor("C-100").disputes()).extracting(Dispute::status)
                .doesNotContain(DisputeStatus.PENDING_CONFIRMATION);
    }

    @Test
    void oneDisputePerTransaction() {
        Dispute first = service.propose("C-100", "T-1006", ReasonCode.DUPLICATE_CHARGE, "Charged twice.");
        Dispute again = service.propose("C-100", "T-1006", ReasonCode.OTHER, "Asking again.");

        assertThat(again).isEqualTo(first);
    }

    @Test
    void cannotProposeForAnotherCustomersTransaction() {
        assertThatThrownBy(() -> service.propose("C-100", "T-3002", ReasonCode.FRAUD, "Not mine."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotConfirmAnotherCustomersDispute() {
        Dispute proposed = service.propose("C-100", "T-1006", ReasonCode.DUPLICATE_CHARGE, "Charged twice.");

        assertThatThrownBy(() -> service.confirm("C-200", proposed.id()))
                .isInstanceOf(DisputeNotFoundException.class);
    }

    @Test
    void cannotConfirmTwice() {
        Dispute proposed = service.propose("C-100", "T-1006", ReasonCode.DUPLICATE_CHARGE, "Charged twice.");
        service.confirm("C-100", proposed.id());

        assertThatThrownBy(() -> service.confirm("C-100", proposed.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findsOpenDisputesOver500() {
        DisputeList result = service.findDisputes(DisputeStatus.OPEN, new BigDecimal("500"));

        assertThat(result.disputes()).extracting(Dispute::id).containsExactly("D-1001", "D-1002");
    }

}
