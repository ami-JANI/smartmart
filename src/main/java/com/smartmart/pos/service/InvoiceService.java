package com.smartmart.pos.service;

/** Builds the invoice of a completed sale (FR-POS-13, SCRUM-32). */
public interface InvoiceService {

    /**
     * The invoice of one sale, read from the primary so it is complete straight
     * after the sale.
     *
     * @throws SaleNotFoundException if no sale has this id
     */
    InvoiceView invoice(Long saleId);
}
