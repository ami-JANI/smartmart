package com.smartmart.pos.service;

/** A payment that does not cover the sale's total (FR-POS-9). The message is written for the cashier. */
public class PaymentRefusedException extends RuntimeException {

    public PaymentRefusedException(String message) {
        super(message);
    }
}
