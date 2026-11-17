package com.smartmart.pos.domain;

import java.math.BigDecimal;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** How a sale was paid: the method, the amount, the cash taken and the change given (FR-POS-9). */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, unique = true)
    private Sale sale;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "cash_received", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashReceived;

    @Column(name = "change_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal changeDue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    Payment(Sale sale, String method, BigDecimal amount, BigDecimal cashReceived, BigDecimal changeDue) {
        this.sale = sale;
        this.method = method;
        this.amount = amount;
        this.cashReceived = cashReceived;
        this.changeDue = changeDue;
    }
}
