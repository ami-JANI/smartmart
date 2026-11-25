package com.smartmart.purchasing.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** An action the purchase order's current state does not allow (FR-PUR-5). The message is written for the person using the page. */
@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(PurchaseOrderStatus status, String action) {
        super("A " + status.getLabel().toLowerCase() + " purchase order cannot be " + action);
    }
}
