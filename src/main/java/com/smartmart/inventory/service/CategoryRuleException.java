package com.smartmart.inventory.service;

/** A category change was refused. The message is written for the person using the page. */
public class CategoryRuleException extends RuntimeException {

    public CategoryRuleException(String message) {
        super(message);
    }
}
