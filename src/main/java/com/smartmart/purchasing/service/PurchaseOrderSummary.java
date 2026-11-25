package com.smartmart.purchasing.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.smartmart.purchasing.domain.PurchaseOrderStatus;

/** A purchase order as the list shows it (FR-PUR-8). */
public record PurchaseOrderSummary(Long id, String supplierName, PurchaseOrderStatus status, Instant createdAt,
                                   int lineCount, BigDecimal total) {
}
