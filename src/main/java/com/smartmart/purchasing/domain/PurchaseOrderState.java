package com.smartmart.purchasing.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The behaviour of a purchase order in one status: the <b>State</b> pattern
 * (GUIDELINES.md section 5.6). {@link PurchaseOrder} is the context and
 * delegates every action to its current state. Each action a state does not
 * override is refused with {@link IllegalStateTransitionException}, so a new
 * status is one new class and no {@code switch} on the status is spread
 * through the services.
 */
public interface PurchaseOrderState {

    /** The status stored for this state. */
    PurchaseOrderStatus status();

    /** Approves a draft (FR-PUR-5). */
    default void submit(PurchaseOrder order) {
        throw refused("submitted");
    }

    /**
     * Records a delivery (FR-PUR-6).
     *
     * @param quantities the quantity received, by purchase order line id
     */
    default void receive(PurchaseOrder order, Map<Long, BigDecimal> quantities) {
        throw refused("received");
    }

    /** Cancels an order whose goods have not started to arrive (FR-PUR-5). */
    default void cancel(PurchaseOrder order) {
        throw refused("cancelled");
    }

    /** Closes an order once nothing more will arrive (FR-PUR-7). */
    default void close(PurchaseOrder order) {
        throw refused("closed");
    }

    private IllegalStateTransitionException refused(String action) {
        return new IllegalStateTransitionException(status(), action);
    }

    /**
     * Rebuilds the state object for a stored status. This is the only place
     * that chooses a class by status.
     */
    static PurchaseOrderState of(PurchaseOrderStatus status) {
        return switch (status) {
            case DRAFT -> new DraftState();
            case SUBMITTED -> new SubmittedState();
            case PARTIALLY_RECEIVED -> new PartiallyReceivedState();
            case RECEIVED -> new ReceivedState();
            case CLOSED -> new ClosedState();
            case CANCELLED -> new CancelledState();
        };
    }
}
