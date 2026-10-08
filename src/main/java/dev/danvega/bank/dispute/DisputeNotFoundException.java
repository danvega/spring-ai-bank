package dev.danvega.bank.dispute;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DisputeNotFoundException extends RuntimeException {

    public DisputeNotFoundException(String disputeId) {
        super("No dispute " + disputeId);
    }

}
