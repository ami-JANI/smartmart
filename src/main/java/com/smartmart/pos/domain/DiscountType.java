package com.smartmart.pos.domain;

import java.math.BigDecimal;

/**
 * The kinds of discount a manager can define (FR-POS-6). Each kind builds its
 * own {@link DiscountStrategy}, so this enum is the only place that turns the
 * stored type into a strategy, and checkout code never branches on it
 * (GUIDELINES.md section 5.1).
 */
public enum DiscountType {

    PERCENTAGE {
        @Override
        public DiscountStrategy strategyFor(BigDecimal value) {
            return new PercentageDiscount(value);
        }

        @Override
        public String describe(BigDecimal value) {
            return value.stripTrailingZeros().toPlainString() + "% off";
        }
    },

    FLAT {
        @Override
        public DiscountStrategy strategyFor(BigDecimal value) {
            return new FlatAmountDiscount(value);
        }

        @Override
        public String describe(BigDecimal value) {
            return "৳ " + value.setScale(2).toPlainString() + " off";
        }
    };

    /** The strategy that works out this kind of discount for {@code value}. */
    public abstract DiscountStrategy strategyFor(BigDecimal value);

    /** A short text for the cashier, for example "10% off" or "৳ 50.00 off". */
    public abstract String describe(BigDecimal value);
}
