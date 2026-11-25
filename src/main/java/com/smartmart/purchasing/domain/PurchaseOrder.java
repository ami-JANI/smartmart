package com.smartmart.purchasing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An order for goods from one supplier (FR-PUR-4 to FR-PUR-7). It is the
 * context in the <b>State</b> pattern (GUIDELINES.md section 5.6): it holds a
 * {@link PurchaseOrderState} and hands every action to it. The status column
 * stores which state it is in, and the state object is rebuilt from it when
 * the order is loaded.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status;

    @Column(length = 255)
    private String note;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    @Transient
    private PurchaseOrderState state;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Starts a draft order with no lines. */
    public PurchaseOrder(Supplier supplier, String note) {
        this.supplier = supplier;
        this.note = note;
        changeState(new DraftState());
    }

    @PostLoad
    void restoreState() {
        state = PurchaseOrderState.of(status);
    }

    /**
     * Adds a product to a draft.
     *
     * @throws IllegalStateTransitionException if the order is no longer a draft
     */
    public void addLine(Long productId, BigDecimal quantity, BigDecimal unitCost) {
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateTransitionException(status, "changed");
        }
        lines.add(new PurchaseOrderLine(this, productId, quantity, unitCost));
    }

    public List<PurchaseOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** The sum of the line totals, in taka. */
    public BigDecimal getTotal() {
        return lines.stream().map(PurchaseOrderLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Returns one of this order's lines.
     *
     * @throws PurchaseOrderRuleException if the line is not on this order
     */
    public PurchaseOrderLine line(Long lineId) {
        return lines.stream()
                .filter(line -> line.getId() != null && line.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new PurchaseOrderRuleException("lineId", "That line is not on this order"));
    }

    public void submit() {
        state.submit(this);
    }

    /**
     * Records a delivery.
     *
     * @param quantities the quantity received, by line id; only lines that received something
     * @throws IllegalStateTransitionException if the order is not waiting for goods
     * @throws PurchaseOrderRuleException      if a quantity is not allowed; nothing is recorded
     */
    public void receive(Map<Long, BigDecimal> quantities) {
        state.receive(this, quantities);
    }

    public void cancel() {
        state.cancel(this);
    }

    public void close() {
        state.close(this);
    }

    /** Called by a state to move the order to the next one. */
    void changeState(PurchaseOrderState next) {
        state = next;
        status = next.status();
    }

    /** Called by a state that accepts deliveries. Checks every quantity before recording any. */
    void recordDelivery(Map<Long, BigDecimal> quantities) {
        if (quantities.isEmpty()) {
            throw new PurchaseOrderRuleException("quantity", "Enter the quantity received for at least one line");
        }
        quantities.forEach((lineId, quantity) -> line(lineId).check(quantity));
        quantities.forEach((lineId, quantity) -> line(lineId).receive(quantity));
    }

    boolean isFullyReceived() {
        return lines.stream().allMatch(line -> line.getOutstanding().signum() == 0);
    }
}
