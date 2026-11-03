package com.smartmart.pos.service;

import java.math.BigDecimal;

/** A cart line as the pages show it. {@code saleUnit} is PIECE or KG; {@code free} marks a free promotion unit. */
public record CartLineView(Long id, String productName, String barcode, String saleUnit,
                           BigDecimal unitPrice, BigDecimal quantity, BigDecimal lineTotal, boolean free) {
}
