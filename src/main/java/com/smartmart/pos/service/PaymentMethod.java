package com.smartmart.pos.service;

import java.math.BigDecimal;

/**
 * A way to pay for a sale. Uses the Strategy pattern (GUIDELINES.md section
 * 5.1): checkout calls {@link #pay} without knowing which method it is, so a
 * new method is a new class. Only {@link CashPayment} exists in v1.
 */
public interface PaymentMethod {

    /**
     * Takes payment of {@code amount}.
     *
     * @return what was paid, ready to be recorded with the sale
     * @throws PaymentRefusedException if the payment does not cover the amount
     */
    PaymentResult pay(BigDecimal amount, PaymentDetails details);
}
