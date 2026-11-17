package com.smartmart.pos.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The current cashier's cart (FR-POS-1 to FR-POS-5 and FR-POS-8). Change methods
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

    /**
     * Picks the sale's one discount, replacing any earlier one (FR-POS-8). It is
     * an undo step like any other change.
     *
     * @param discountId an id from {@link DiscountService#activeDiscounts()}, or null to remove the discount
     * @throws com.smartmart.pos.domain.CartRuleException if that discount is not valid today
     */
    void applyDiscount(Long discountId);

    /**
     * Records which member is buying, found by phone number. It is not an undo step.
     *
     * @throws com.smartmart.pos.domain.CartRuleException if no member has that phone number
     */
    void addMember(String phone);

    /** Removes the member from the sale. */
    void removeMember();

    /** The member buying, if one was added. */
    Optional<CustomerSummary> member();

    /** Reverses the last change, one step at a time. Refused when there is nothing to undo. */
    void undo();

    /** Empties the cart and its undo history, ready for the next sale. */
    void clear();
}
