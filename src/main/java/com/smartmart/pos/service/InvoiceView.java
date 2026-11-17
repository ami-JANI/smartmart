package com.smartmart.pos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything templates/pos/invoice.html shows (FR-POS-13). Promotions are not
 * built yet, so promotionSavings is always 0 and no line is free.
 */
public record InvoiceView(String number, Instant soldAt, String cashierName, List<Line> lines,
                          BigDecimal subtotal, BigDecimal promotionSavings, String discountName,
                          BigDecimal discountAmount, BigDecimal total, BigDecimal vatIncluded,
                          BigDecimal cashReceived, BigDecimal changeDue) {

    /** One invoice line, at the price the product was sold for. */
    public record Line(String productName, String saleUnit, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal lineTotal, boolean free) {
    }
}
