package com.smartmart.pos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A completed sale, which is also its invoice (FR-POS-10, FR-POS-13). It owns
 * its lines and its payment, so saving the sale saves all of them in the same
 * transaction. The cashier and discount are kept as ids, because they belong
 * to other parts of the system.
 */
@Entity
@Table(name = "sales")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "discount_id")
    private Long discountId;

    @Column(name = "sold_at", nullable = false)
    private Instant soldAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "vat_included", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatIncluded;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SaleLine> lines = new ArrayList<>();

    @OneToOne(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Sale(Long cashierId, Long discountId, Instant soldAt, BigDecimal subtotal, BigDecimal discountAmount,
                BigDecimal total, BigDecimal vatIncluded) {
        this.cashierId = cashierId;
        this.discountId = discountId;
        this.soldAt = soldAt;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.total = total;
        this.vatIncluded = vatIncluded;
    }

    public void addLine(Long productId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        lines.add(new SaleLine(this, productId, quantity, unitPrice, lineTotal));
    }

    public void recordPayment(String method, BigDecimal amount, BigDecimal cashReceived, BigDecimal changeDue) {
        payment = new Payment(this, method, amount, cashReceived, changeDue);
    }

    public List<SaleLine> getLines() {
        return List.copyOf(lines);
    }

    /** The invoice number shown to the customer, for example INV-000042. */
    public String invoiceNumber() {
        return "INV-%06d".formatted(id);
    }
}
