package com.smartmart.pos.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The current cashier's cart (FR-POS-1 to FR-POS-3 and FR-POS-5). Change methods
 * throw {@link com.smartmart.pos.domain.CartRuleException} when a change is refused,
 * leaving the cart as it was.
 */
public interface CartService {

    CartView view();

    /**
     * Adds a scanned product: one unit of a piece product, or {@code weight} kg of a weighed one.
     *
     * @param weight the weight for a weighed product, or null if not yet known
     * @return a prompt when a weighed product still needs its weight; nothing was added then
     */
    Optional<WeightPrompt> scan(String barcode, BigDecimal weight);

    /** Sets a line's quantity. {@code lineId} comes from {@link CartLineView#id()}. */
    void changeQuantity(Long lineId, BigDecimal quantity);

    /** Removes a line. {@code lineId} comes from {@link CartLineView#id()}. */
    void remove(Long lineId);
}
