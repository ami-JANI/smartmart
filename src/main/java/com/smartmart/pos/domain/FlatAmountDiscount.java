package com.smartmart.pos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A fixed amount off, capped at the total it applies to: a flat 100.00 on
 * 60.00 is 60.00 (FR-POS-8). A concrete strategy for {@link DiscountStrategy}.
 * The amount is greater than 0 (FR-POS-6).
 */
public record FlatAmountDiscount(BigDecimal amount) implements DiscountStrategy {

    public FlatAmountDiscount {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("A flat discount must be greater than 0");
        }
    }

    @Override
    public BigDecimal discountOn(BigDecimal eligibleTotal) {
        if (eligibleTotal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.min(eligibleTotal).setScale(2, RoundingMode.HALF_UP);
    }
}
