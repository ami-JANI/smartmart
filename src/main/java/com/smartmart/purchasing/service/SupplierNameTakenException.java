package com.smartmart.purchasing.service;

/** Another supplier already has this name. The message is written for the person using the page. */
public class SupplierNameTakenException extends RuntimeException {

    public SupplierNameTakenException() {
        super("Supplier name already in use");
    }
}
