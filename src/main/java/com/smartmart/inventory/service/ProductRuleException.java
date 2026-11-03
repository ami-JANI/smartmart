package com.smartmart.inventory.service;

import lombok.Getter;

/** A product or stock batch could not be saved because of one field. The message is written for the person using the page. */
@Getter
public class ProductRuleException extends RuntimeException {

    private final String field;

    public ProductRuleException(String field, String message) {
        super(message);
        this.field = field;
    }
}
