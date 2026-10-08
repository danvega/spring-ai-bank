package dev.danvega.bank.dispute;

import dev.danvega.bank.customer.CurrentCustomer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The customer's view of their disputes. Confirming happens here, never in the chat. */
@RestController
@RequestMapping("/api/disputes")
class DisputeController {

    private final DisputeService disputeService;
    private final CurrentCustomer currentCustomer;

    DisputeController(DisputeService disputeService, CurrentCustomer currentCustomer) {
        this.disputeService = disputeService;
        this.currentCustomer = currentCustomer;
    }

    @GetMapping
    DisputeList myDisputes() {
        return disputeService.disputesFor(currentCustomer.id());
    }

    @PostMapping("/{id}/confirm")
    Dispute confirm(@PathVariable String id) {
        return disputeService.confirm(currentCustomer.id(), id);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String alreadyConfirmed(IllegalStateException e) {
        return e.getMessage();
    }

}
