package com.smartmart.purchasing.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import lombok.Getter;

/** A purchase order or delivery could not be saved because of one field. The message is written for the person using the page. */
@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PurchaseOrderRuleException extends RuntimeException {

    private final String field;

    public PurchaseOrderRuleException(String field, String message) {
        super(message);
        this.field = field;
    }
}
