package com.smartmart.pos.service;

import java.math.BigDecimal;

/** A payment that was taken: its method, the amount paid, the cash received and the change due. */
public record PaymentResult(String method, BigDecimal amount, BigDecimal cashReceived, BigDecimal changeDue) {
}
