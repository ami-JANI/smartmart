package com.smartmart.pos.service;

import java.math.BigDecimal;

/** What the cashier entered on the payment page: the cash the customer handed over. */
public record PaymentDetails(BigDecimal cashReceived) {
}
