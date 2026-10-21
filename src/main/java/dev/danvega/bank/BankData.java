package dev.danvega.bank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.danvega.bank.dispute.Dispute;
import dev.danvega.bank.dispute.DisputeStatus;
import dev.danvega.bank.dispute.ReasonCode;
import dev.danvega.bank.transaction.Transaction;

public final class BankData {

    private BankData() {
    }

    public static List<Transaction> transactions() {
        return List.of(
                tx("T-1001", "C-100", "4242", "Fresh Market", "84.12", "2026-09-28"),
                tx("T-1002", "C-100", "4242", "City Transit", "2.75", "2026-09-30"),
                tx("T-1003", "C-100", "1881", "Streamly", "15.99", "2026-10-01"),
                tx("T-1004", "C-100", "4242", "Fuel Stop", "52.40", "2026-10-03"),
                tx("T-1005", "C-100", "4242", "Acme Coffee", "18.75", "2026-10-05"),
                tx("T-1006", "C-100", "4242", "Acme Coffee", "18.75", "2026-10-05"),
                tx("T-1007", "C-100", "4242", "Book Nook", "27.00", "2026-10-06"),
                tx("T-1008", "C-100", "1881", "Online Electronics", "649.00", "2026-10-07"),
                tx("T-1009", "C-100", "4242", "Concert Hall", "310.00", "2026-08-15"),

                tx("T-2001", "C-200", "7310", "Hotel Marlow", "780.00", "2026-08-22"),
                tx("T-2002", "C-200", "7310", "Acme Coffee", "4.25", "2026-10-02"),
                tx("T-2003", "C-200", "7310", "Skyline Airlines", "1240.00", "2026-09-12"),

                tx("T-3001", "C-300", "5566", "Fresh Market", "61.30", "2026-09-29"),
                tx("T-3002", "C-300", "5566", "Luxe Furniture", "2150.00", "2026-09-30"),
                tx("T-3003", "C-300", "5566", "Pet Palace", "45.10", "2026-10-02"),
                tx("T-3004", "C-300", "5566", "Gadget Hub", "89.99", "2026-10-04"));
    }

    public static List<Dispute> disputes() {
        return List.of(
                dispute("D-1001", "C-200", "T-2003", "Skyline Airlines", "1240.00", ReasonCode.NOT_RECEIVED,
                        "Flight was cancelled and never refunded.", DisputeStatus.OPEN, "2026-09-20"),
                dispute("D-1002", "C-300", "T-3002", "Luxe Furniture", "2150.00", ReasonCode.FRAUD,
                        "I never bought furniture. My card was in my wallet.", DisputeStatus.OPEN, "2026-10-01"),
                dispute("D-1003", "C-300", "T-3004", "Gadget Hub", "89.99", ReasonCode.WRONG_AMOUNT,
                        "The price at the till was 59.99.", DisputeStatus.OPEN, "2026-10-05"),
                dispute("D-1004", "C-200", "T-2001", "Hotel Marlow", "780.00", ReasonCode.WRONG_AMOUNT,
                        "Charged for three nights, stayed two.", DisputeStatus.RESOLVED, "2026-08-25"),
                dispute("D-1000", "C-100", "T-1009", "Concert Hall", "310.00", ReasonCode.NOT_RECEIVED,
                        "The concert was cancelled.", DisputeStatus.RESOLVED, "2026-08-20"));
    }

    private static Transaction tx(String id, String customerId, String card, String merchant, String amount,
            String date) {
        return new Transaction(id, customerId, card, merchant, new BigDecimal(amount), LocalDate.parse(date));
    }

    private static Dispute dispute(String id, String customerId, String transactionId, String merchant,
            String amount, ReasonCode reason, String explanation, DisputeStatus status, String createdOn) {
        return new Dispute(id, customerId, transactionId, merchant, new BigDecimal(amount), reason, explanation,
                status, LocalDate.parse(createdOn));
    }

}
