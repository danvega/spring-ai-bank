package dev.danvega.bank.customer;

import org.springframework.stereotype.Component;

// Demo only. A real app gets this from Spring Security.
@Component
public class CurrentCustomer {

    public static final String MARIA = "C-100";

    public String id() {
        return MARIA;
    }

}
