package com.smartmart.inventory.service;

import java.math.BigDecimal;

/** A product as the pages show it. {@code saleUnit} is PIECE or KG. */
public record ProductSummary(Long id, String name, String barcode, Long categoryId, String categoryName,
                             BigDecimal price, String saleUnit, BigDecimal lowStockThreshold, boolean active,
                             BigDecimal totalStock, boolean lowStock) {
}
