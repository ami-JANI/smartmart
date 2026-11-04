package com.smartmart.pos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A percentage off, for example 10% of 500.00 is 50.00. A concrete strategy
 * for {@link DiscountStrategy}. The percentage is greater than 0 and at most
 * 100 (FR-POS-6); the amount is rounded half-up to 2 decimals.
 */
public record PercentageDiscount(BigDecimal percent) implements DiscountStrategy {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public PercentageDiscount {
        if (percent.signum() <= 0 || percent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("A percentage discount must be greater than 0 and at most 100");
        }
    }

    @Override
    public BigDecimal discountOn(BigDecimal eligibleTotal) {
        if (eligibleTotal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return eligibleTotal.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
