package com.smartmart.pos.domain;

import java.math.BigDecimal;

/**
 * How a discount is worked out. Uses the Strategy pattern (GUIDELINES.md
 * section 5.1): each kind of discount is its own class, so a new kind is
 * added without changing checkout code.
 * <p>
 * Contract (FR-POS-8): the result is never negative and never more than the
 * total it is applied to, and has 2 decimal places.
 */
public interface DiscountStrategy {

    /**
     * @param eligibleTotal the total of the cart lines the discount applies to
     * @return the amount taken off that total
     */
    BigDecimal discountOn(BigDecimal eligibleTotal);
}
