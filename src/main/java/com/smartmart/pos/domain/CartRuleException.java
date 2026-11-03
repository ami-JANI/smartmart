package com.smartmart.pos.domain;

/** A cart change was refused. The message is written for the cashier. */
public class CartRuleException extends RuntimeException {

    public CartRuleException(String message) {
        super(message);
    }
}
