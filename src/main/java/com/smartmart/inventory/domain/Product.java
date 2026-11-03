package com.smartmart.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A product the store sells. This is the abstraction in the
 * Abstraction-Occurrence pattern: it holds the shared data, and each delivery
 * of it is a stock batch.
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_unit", nullable = false, length = 10)
    private SaleUnit saleUnit;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "low_stock_threshold", precision = 12, scale = 3)
    private BigDecimal lowStockThreshold;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Product(String name, String barcode, Category category, SaleUnit saleUnit,
                   BigDecimal price, BigDecimal lowStockThreshold, boolean active) {
        update(name, barcode, category, saleUnit, price, lowStockThreshold, active);
    }

    public void update(String name, String barcode, Category category, SaleUnit saleUnit,
                       BigDecimal price, BigDecimal lowStockThreshold, boolean active) {
        this.name = name;
        this.barcode = barcode;
        this.category = category;
        this.saleUnit = saleUnit;
        this.price = price;
        this.lowStockThreshold = lowStockThreshold;
        this.active = active;
    }

    /** A product is low on stock at or below its own threshold, or the store default if it has none (FR-INV-8). */
    public boolean isLowOnStock(BigDecimal totalStock, BigDecimal storeDefault) {
        BigDecimal threshold = lowStockThreshold != null ? lowStockThreshold : storeDefault;
        return totalStock.compareTo(threshold) <= 0;
    }
}
