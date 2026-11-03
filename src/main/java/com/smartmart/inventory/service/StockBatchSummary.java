package com.smartmart.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A stock batch as the pages show it. */
public record StockBatchSummary(String batchNumber, BigDecimal quantity, BigDecimal cost, LocalDate expiryDate,
                                boolean expired) {
}
