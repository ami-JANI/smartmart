package com.smartmart.pos.service;

import java.util.List;
import java.util.Optional;

/** The discounts managers defined, as the cashier sees them (FR-POS-6, FR-POS-8). */
public interface DiscountService {

    /** Discounts valid today, by name. May come from the replica. */
    List<DiscountOption> activeDiscounts();

    /**
     * Returns the discount with this id if it is valid today. Runs in the
     * caller's transaction when there is one, so during checkout it reads the
     * primary.
     */
    Optional<ActiveDiscount> findActive(Long id);
}
