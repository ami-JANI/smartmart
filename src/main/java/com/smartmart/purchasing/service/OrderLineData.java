package com.smartmart.purchasing.service;

import java.math.BigDecimal;

/** One product a person puts on a new purchase order. */
public record OrderLineData(Long productId, BigDecimal quantity, BigDecimal unitCost) {
}
