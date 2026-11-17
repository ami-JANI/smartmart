package com.smartmart.pos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * Cash at the till (FR-POS-9): the cash received must be at least the amount,
 * and the change due is the difference. A concrete strategy for
 * {@link PaymentMethod}.
 */
@Component
class CashPayment implements PaymentMethod {

    static final String METHOD = "CASH";

    @Override
    public PaymentResult pay(BigDecimal amount, PaymentDetails details) {
        BigDecimal cash = details.cashReceived();
        if (cash == null || cash.compareTo(amount) < 0) {
            throw new PaymentRefusedException("Cash received is less than the total");
        }
        BigDecimal scaledCash = cash.setScale(2, RoundingMode.HALF_UP);
        return new PaymentResult(METHOD, amount, scaledCash, scaledCash.subtract(amount));
    }
}
