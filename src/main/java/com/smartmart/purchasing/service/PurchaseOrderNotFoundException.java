package com.smartmart.purchasing.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PurchaseOrderNotFoundException extends RuntimeException {

    public PurchaseOrderNotFoundException(Long id) {
        super("Purchase order not found: " + id);
    }
}
