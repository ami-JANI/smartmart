package com.smartmart.pos.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.smartmart.inventory.domain.SaleUnit;

/**
 * One product in the cart, with its price as scanned. Immutable, and always
 * holds a quantity that is valid for its sale unit (FR-POS-2, FR-POS-3).
 */
public record CartLine(Long productId, String productName, String barcode, SaleUnit saleUnit,
                       BigDecimal unitPrice, BigDecimal quantity) implements Serializable {

    static final BigDecimal MAX_QUANTITY = new BigDecimal("9999");

    public CartLine {
        if (quantity.signum() <= 0 || !saleUnit.allows(quantity)) {
            throw new CartRuleException(saleUnit == SaleUnit.KG
                    ? "Weight must be greater than 0, with at most 3 decimals"
                    : "Quantity must be a whole number of at least 1");
        }
        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new CartRuleException("Quantity can be at most " + MAX_QUANTITY);
        }
    }

    CartLine withQuantity(BigDecimal newQuantity) {
        return new CartLine(productId, productName, barcode, saleUnit, unitPrice, newQuantity);
    }

    public BigDecimal total() {
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
}
