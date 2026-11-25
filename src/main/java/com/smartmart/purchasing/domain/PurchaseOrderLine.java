package com.smartmart.purchasing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One product on a purchase order. The product is kept as an id, because it
 * belongs to the inventory module (GUIDELINES.md section 3.4).
 */
@Entity
@Table(name = "purchase_order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "received_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal receivedQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    PurchaseOrderLine(PurchaseOrder order, Long productId, BigDecimal quantity, BigDecimal unitCost) {
        this.order = order;
        this.productId = productId;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.receivedQuantity = BigDecimal.ZERO;
    }

    /** What is still to arrive. */
    public BigDecimal getOutstanding() {
        return quantity.subtract(receivedQuantity);
    }

    /** Quantity × unit cost, in taka. */
    public BigDecimal getLineTotal() {
        return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Checks a delivered quantity without recording it.
     *
     * @throws PurchaseOrderRuleException if it is not more than 0, or more than is outstanding
     */
    void check(BigDecimal delivered) {
        if (delivered.signum() <= 0) {
            throw new PurchaseOrderRuleException("quantity", "A received quantity must be more than 0");
        }
        if (delivered.compareTo(getOutstanding()) > 0) {
            throw new PurchaseOrderRuleException("quantity",
                    "Only " + getOutstanding().stripTrailingZeros().toPlainString() + " is still to arrive on this line");
        }
    }

    /** Adds a delivered quantity, after {@link #check}. */
    void receive(BigDecimal delivered) {
        check(delivered);
        receivedQuantity = receivedQuantity.add(delivered);
    }
}
