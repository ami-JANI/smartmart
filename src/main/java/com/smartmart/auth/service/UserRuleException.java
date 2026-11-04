package com.smartmart.auth.service;

import lombok.Getter;

/**
 * A user could not be saved or changed. {@code field} names the form field at
 * fault, or is null when the refusal is about the whole request. The message
 * is written for the person using the page.
 */
@Getter
public class UserRuleException extends RuntimeException {

    private final String field;

    public UserRuleException(String field, String message) {
        super(message);
        this.field = field;
    }
}
