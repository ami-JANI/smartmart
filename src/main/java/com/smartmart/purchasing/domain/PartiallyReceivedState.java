package com.smartmart.purchasing.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * An order with some goods received and some still outstanding. More can be
 * received, or it can be closed when the supplier will not send the rest.
 * It cannot be cancelled, because goods have already arrived.
 */
class PartiallyReceivedState implements PurchaseOrderState {

    @Override
    public PurchaseOrderStatus status() {
        return PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    @Override
    public void receive(PurchaseOrder order, Map<Long, BigDecimal> quantities) {
        order.recordDelivery(quantities);
        order.changeState(order.isFullyReceived() ? new ReceivedState() : new PartiallyReceivedState());
    }

    @Override
    public void close(PurchaseOrder order) {
        order.changeState(new ClosedState());
    }
}
