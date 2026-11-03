package com.smartmart.inventory.service;

import java.math.BigDecimal;

import com.smartmart.inventory.domain.SaleUnit;

/** What a person enters for a product. {@code lowStockThreshold} is null to use the store default. */
public record ProductData(String name, String barcode, Long categoryId, SaleUnit saleUnit,
                          BigDecimal price, BigDecimal lowStockThreshold, boolean active) {
}
