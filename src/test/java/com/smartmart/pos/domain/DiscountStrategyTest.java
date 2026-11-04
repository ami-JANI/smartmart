package com.smartmart.pos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Boundary values for the two discount strategies (GUIDELINES.md section 9):
 * percentages 0, 1, 100 and just above, flat amounts below, at and above the
 * eligible total, and an empty eligible total.
 */
class DiscountStrategyTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal percentOf(String percent, String total) {
        return new PercentageDiscount(money(percent)).discountOn(money(total));
    }

    private static BigDecimal flatOn(String amount, String total) {
        return new FlatAmountDiscount(money(amount)).discountOn(money(total));
    }

    @Test
    void zeroPercentIsRefused() {
        assertThatThrownBy(() -> new PercentageDiscount(money("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onePercentIsAllowed() {
        assertThat(percentOf("1", "500.00")).isEqualByComparingTo("5.00");
    }

    @Test
    void hundredPercentTakesTheWholeTotal() {
        assertThat(percentOf("100", "500.00")).isEqualByComparingTo("500.00");
    }

    @Test
    void justAboveHundredPercentIsRefused() {
        assertThatThrownBy(() -> new PercentageDiscount(money("100.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePercentIsRefused() {
        assertThatThrownBy(() -> new PercentageDiscount(money("-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tenPercentOfFiveHundredIsFifty() {
        assertThat(percentOf("10", "500.00")).isEqualByComparingTo("50.00");
    }

    @Test
    void percentageRoundsHalfUpToTwoDecimals() {
        assertThat(percentOf("12.5", "0.20")).isEqualTo(money("0.03"));
        assertThat(percentOf("10", "0.04")).isEqualTo(money("0.00"));
    }

    @Test
    void percentageOfNothingIsZero() {
        assertThat(percentOf("10", "0.00")).isEqualTo(money("0.00"));
    }

    @Test
    void zeroFlatAmountIsRefused() {
        assertThatThrownBy(() -> new FlatAmountDiscount(money("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flatAmountBelowTheTotalIsTakenInFull() {
        assertThat(flatOn("50", "60.00")).isEqualTo(money("50.00"));
    }

    @Test
    void flatAmountEqualToTheTotalTakesTheTotal() {
        assertThat(flatOn("60.00", "60.00")).isEqualTo(money("60.00"));
    }

    @Test
    void flatAmountAboveTheTotalIsCappedAtTheTotal() {
        assertThat(flatOn("100.00", "60.00")).isEqualTo(money("60.00"));
    }

    @Test
    void flatAmountOnNothingIsZero() {
        assertThat(flatOn("100.00", "0.00")).isEqualTo(money("0.00"));
    }

    @Test
    void neverNegativeAndNeverAboveTheTotal() {
        DiscountStrategy[] strategies = {
                new PercentageDiscount(money("1")), new PercentageDiscount(money("100")),
                new FlatAmountDiscount(money("0.01")), new FlatAmountDiscount(money("1000000"))};
        String[] totals = {"0.00", "0.01", "59.99", "500.00", "9999999.99"};
        for (DiscountStrategy strategy : strategies) {
            for (String total : totals) {
                BigDecimal discount = strategy.discountOn(money(total));
                assertThat(discount).as("%s on %s", strategy, total)
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO)
                        .isLessThanOrEqualTo(money(total));
            }
        }
    }
}
