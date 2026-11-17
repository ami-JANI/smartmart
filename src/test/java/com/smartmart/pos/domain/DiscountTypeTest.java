package com.smartmart.pos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DiscountTypeTest {

    @Test
    void eachTypeBuildsItsOwnStrategy() {
        assertThat(DiscountType.PERCENTAGE.strategyFor(new BigDecimal("10")))
                .isEqualTo(new PercentageDiscount(new BigDecimal("10")));
        assertThat(DiscountType.FLAT.strategyFor(new BigDecimal("50.00")))
                .isEqualTo(new FlatAmountDiscount(new BigDecimal("50.00")));
    }

    @Test
    void descriptionsAreShortTextForTheCashier() {
        assertThat(DiscountType.PERCENTAGE.describe(new BigDecimal("10.00"))).isEqualTo("10% off");
        assertThat(DiscountType.PERCENTAGE.describe(new BigDecimal("12.50"))).isEqualTo("12.5% off");
        assertThat(DiscountType.FLAT.describe(new BigDecimal("50"))).isEqualTo("৳ 50.00 off");
    }

    @Test
    void discountUsesTheStrategyOfItsType() {
        Discount discount = new Discount("Staff", DiscountType.PERCENTAGE, new BigDecimal("10.00"), null, null);

        assertThat(discount.strategy().discountOn(new BigDecimal("500.00"))).isEqualByComparingTo("50.00");
        assertThat(discount.description()).isEqualTo("10% off");
    }
}
