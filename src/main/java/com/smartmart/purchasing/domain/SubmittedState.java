package com.smartmart.purchasing.domain;

import java.math.BigDecimal;
import java.util.Map;

/** An approved order waiting for its goods. It can be received or cancelled. */
class SubmittedState implements PurchaseOrderState {

    @Override
    public PurchaseOrderStatus status() {
        return PurchaseOrderStatus.SUBMITTED;
    }

    @Override
    public void receive(PurchaseOrder order, Map<Long, BigDecimal> quantities) {
        order.recordDelivery(quantities);
        order.changeState(order.isFullyReceived() ? new ReceivedState() : new PartiallyReceivedState());
    }

    @Override
    public void cancel(PurchaseOrder order) {
        order.changeState(new CancelledState());
    }
}
