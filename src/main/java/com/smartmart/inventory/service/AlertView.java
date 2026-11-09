package com.smartmart.inventory.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.smartmart.inventory.domain.AlertType;

/**
 * Details of an inventory alert, shown on {@code /alerts} (SCRUM-34, SCRUM-53).
 */
public record AlertView(
        Long id,
        AlertType type,
        Long productId,
        String productName,
        Instant raisedAt,
        BigDecimal totalStock,
        BigDecimal threshold,
        String batchNumber,
        BigDecimal quantity,
        LocalDate expiryDate
) {
}
