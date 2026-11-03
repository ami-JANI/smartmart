package com.smartmart.inventory.domain;

import java.math.BigDecimal;

/** How a product is sold (docs/decisions.md). */
public enum SaleUnit {

    /** Whole units. */
    PIECE(0),

    /** Kilograms, with at most 3 decimal places. */
    KG(3);

    private final int maxDecimals;

    SaleUnit(int maxDecimals) {
        this.maxDecimals = maxDecimals;
    }

    /** Says whether {@code quantity} has no more decimal places than this unit allows (FR-INV-5). */
    public boolean allows(BigDecimal quantity) {
        return quantity.stripTrailingZeros().scale() <= maxDecimals;
    }
}
