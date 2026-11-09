package com.smartmart.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
 * An alert raised when stock is low or a batch is close to expiry (FR-INV-8, FR-INV-9).
 * Part of the Observer pattern (GUIDELINES.md section 5.2).
 */
@Entity
@Table(name = "stock_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 20)
    private AlertType alertType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "batch_number", length = 50)
    private String batchNumber;

    @Column(name = "total_stock", precision = 12, scale = 3)
    private BigDecimal totalStock;

    @Column(precision = 12, scale = 3)
    private BigDecimal threshold;

    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StockAlert lowStock(Product product, BigDecimal totalStock, BigDecimal threshold, Instant raisedAt) {
        StockAlert alert = new StockAlert();
        alert.alertType = AlertType.LOW_STOCK;
        alert.product = product;
        alert.totalStock = totalStock;
        alert.threshold = threshold;
        alert.status = AlertStatus.OPEN;
        alert.raisedAt = raisedAt;
        return alert;
    }

    public static StockAlert nearExpiry(Product product, String batchNumber, BigDecimal quantity,
                                        LocalDate expiryDate, Instant raisedAt) {
        StockAlert alert = new StockAlert();
        alert.alertType = AlertType.NEAR_EXPIRY;
        alert.product = product;
        alert.batchNumber = batchNumber;
        alert.quantity = quantity;
        alert.expiryDate = expiryDate;
        alert.status = AlertStatus.OPEN;
        alert.raisedAt = raisedAt;
        return alert;
    }

    public void updateLowStock(BigDecimal totalStock, BigDecimal threshold, Instant raisedAt) {
        this.totalStock = totalStock;
        this.threshold = threshold;
        this.raisedAt = raisedAt;
        this.status = AlertStatus.OPEN;
    }

    public void updateNearExpiry(BigDecimal quantity, LocalDate expiryDate, Instant raisedAt) {
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.raisedAt = raisedAt;
        this.status = AlertStatus.OPEN;
    }

    public void resolve() {
        this.status = AlertStatus.RESOLVED;
    }

    public boolean isOpen() {
        return this.status == AlertStatus.OPEN;
    }
}
