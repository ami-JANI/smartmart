package com.smartmart.purchasing.domain;

/** An order that was called off before any goods arrived. Every action is refused. */
class CancelledState implements PurchaseOrderState {

    @Override
    public PurchaseOrderStatus status() {
        return PurchaseOrderStatus.CANCELLED;
    }
}
