package com.smartmart.purchasing.domain;

/** A finished order. Every action is refused. */
class ClosedState implements PurchaseOrderState {

    @Override
    public PurchaseOrderStatus status() {
        return PurchaseOrderStatus.CLOSED;
    }
}
