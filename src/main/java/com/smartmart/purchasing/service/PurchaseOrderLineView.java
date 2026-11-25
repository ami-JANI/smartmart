package com.smartmart.purchasing.service;

import java.math.BigDecimal;

/** One line of a purchase order, with the product's current name and barcode. */
public record PurchaseOrderLineView(Long lineId, Long productId, String productName, String barcode,
                                    BigDecimal quantity, BigDecimal unitCost, BigDecimal lineTotal,
                                    BigDecimal receivedQuantity, BigDecimal outstanding) {
}
