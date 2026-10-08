package dev.danvega.bank.dispute;

import dev.danvega.bank.customer.CurrentCustomer;
import dev.danvega.bank.transaction.TransactionService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DisputeController.class)
@Import({ DisputeService.class, TransactionService.class, CurrentCustomer.class })
class DisputeControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    DisputeService disputeService;

    @Test
    void listsOnlyMyDisputes() throws Exception {
        mvc.perform(get("/api/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputes[*].customerId").value(everyItem(is("C-100"))));
    }

    @Test
    void confirmsMyPendingDispute() throws Exception {
        Dispute proposed = disputeService.propose("C-100", "T-1005", ReasonCode.DUPLICATE_CHARGE, "Twice.");

        mvc.perform(post("/api/disputes/{id}/confirm", proposed.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void anotherCustomersDisputeIsNotFound() throws Exception {
        mvc.perform(post("/api/disputes/D-1002/confirm"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aResolvedDisputeCannotBeConfirmed() throws Exception {
        mvc.perform(post("/api/disputes/D-1000/confirm"))
                .andExpect(status().isConflict());
    }

}
