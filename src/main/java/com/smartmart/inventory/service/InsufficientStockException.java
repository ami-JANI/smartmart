package com.smartmart.inventory.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A sale needs more of a product than its unexpired batches hold (FR-POS-12). The message is written for the cashier. */
@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productName) {
        super("Not enough stock for " + productName);
    }
}
