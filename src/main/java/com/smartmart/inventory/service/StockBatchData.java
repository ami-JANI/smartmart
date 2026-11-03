package com.smartmart.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What a person enters for a delivery. {@code expiryDate} is null for goods that do not expire. */
public record StockBatchData(String batchNumber, BigDecimal quantity, BigDecimal cost, LocalDate expiryDate) {
}
