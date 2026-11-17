package com.smartmart.pos.service;

/** A member could not be registered. {@code field} names the form field the message belongs to. */
public class CustomerRuleException extends RuntimeException {

    private final String field;

    public CustomerRuleException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
