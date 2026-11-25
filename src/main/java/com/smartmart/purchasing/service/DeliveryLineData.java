package com.smartmart.purchasing.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a person enters for one purchase order line when goods arrive. A null
 * or zero quantity means nothing arrived for the line. {@code expiryDate} is
 * null for goods that do not expire.
 */
public record DeliveryLineData(Long lineId, BigDecimal quantity, String batchNumber, LocalDate expiryDate) {
}
