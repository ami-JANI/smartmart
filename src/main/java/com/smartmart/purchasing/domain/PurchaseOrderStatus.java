package com.smartmart.purchasing.domain;

/** The status stored for a purchase order. Each one has a {@link PurchaseOrderState} with its behaviour. */
public enum PurchaseOrderStatus {

    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    PARTIALLY_RECEIVED("Partially received"),
    RECEIVED("Received"),
    CLOSED("Closed"),
    CANCELLED("Cancelled");

    private final String label;

    PurchaseOrderStatus(String label) {
        this.label = label;
    }

    /** The name shown on pages, for example "Partially received". */
    public String getLabel() {
        return label;
    }
}
