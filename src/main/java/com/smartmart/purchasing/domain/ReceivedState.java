package com.smartmart.purchasing.domain;

/** An order whose goods have all arrived. It can only be closed. */
class ReceivedState implements PurchaseOrderState {

    @Override
    public PurchaseOrderStatus status() {
        return PurchaseOrderStatus.RECEIVED;
    }

    @Override
    public void close(PurchaseOrder order) {
        order.changeState(new ClosedState());
    }
}
