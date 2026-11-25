package com.smartmart.purchasing.domain;

/** A new order that a manager has not approved yet. It can be submitted or cancelled. */
class DraftState implements PurchaseOrderState {

    @Override
    public PurchaseOrderStatus status() {
        return PurchaseOrderStatus.DRAFT;
    }

    @Override
    public void submit(PurchaseOrder order) {
        order.changeState(new SubmittedState());
    }

    @Override
    public void cancel(PurchaseOrder order) {
        order.changeState(new CancelledState());
    }
}
