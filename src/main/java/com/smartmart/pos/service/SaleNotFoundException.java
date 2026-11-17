package com.smartmart.pos.service;

/** No sale has the requested id. */
public class SaleNotFoundException extends RuntimeException {

    public SaleNotFoundException(Long saleId) {
        super("No sale with id " + saleId);
    }
}
