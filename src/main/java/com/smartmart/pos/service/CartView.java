package com.smartmart.pos.service;

import java.math.BigDecimal;
import java.util.List;

/** The cart and its totals as the pages show them (FR-POS-5). */
public record CartView(List<CartLineView> lines, BigDecimal subtotal, BigDecimal promotionSavings,
                       Long discountId, String discountName, BigDecimal discountAmount,
                       BigDecimal total, BigDecimal vatIncluded, boolean canUndo) {
}
