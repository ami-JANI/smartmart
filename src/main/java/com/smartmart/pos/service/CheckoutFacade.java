package com.smartmart.pos.service;

/**
 * Completes the current cashier's sale in one call (FR-POS-9 to FR-POS-12).
 * Uses the Facade pattern (GUIDELINES.md section 5.5): the page calls only
 * this, although a sale touches the cart, the discount, the payment, the
 * stock and the invoice.
 */
public interface CheckoutFacade {

    /**
     * Takes payment for the cart's total, deducts every line from stock
     * first-expiry-first-out, and saves the sale with its lines and payment.
     * All of it runs in one transaction on the primary, so it is saved
     * together or not at all (FR-POS-10). Only after the commit is the cart
     * and its undo history cleared; after any failure the cart is unchanged.
     *
     * @param cashierId the logged-in cashier's user id
     * @return the id of the saved sale, which is also its invoice
     * @throws com.smartmart.pos.domain.CartRuleException              if the cart is empty
     * @throws PaymentRefusedException                                 if the payment does not cover the total
     * @throws com.smartmart.inventory.service.InsufficientStockException if a line needs more than the
     *                                                                  product's unexpired stock
     */
    Long completeSale(Long cashierId, PaymentDetails payment);
}
