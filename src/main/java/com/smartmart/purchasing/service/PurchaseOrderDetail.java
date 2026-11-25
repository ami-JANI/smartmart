package com.smartmart.purchasing.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.smartmart.purchasing.domain.PurchaseOrderStatus;

/** One purchase order with its lines, as its page shows it. */
public record PurchaseOrderDetail(Long id, Long supplierId, String supplierName, PurchaseOrderStatus status,
                                  String note, Instant createdAt, List<PurchaseOrderLineView> lines,
                                  BigDecimal total) {
}
