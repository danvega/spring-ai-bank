package dev.danvega.bank.customer;

import org.springframework.stereotype.Component;

/**
 * The signed-in customer. This demo has no real sign-in.
 * In a real app this would come from Spring Security.
 */
@Component
public class CurrentCustomer {

    public static final String MARIA = "C-100";

    public String id() {
        return MARIA;
    }

}
