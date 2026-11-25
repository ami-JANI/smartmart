package com.smartmart.purchasing.service;

import java.util.List;

/** What a person enters for a new purchase order. {@code note} is optional. */
public record PurchaseOrderData(Long supplierId, String note, List<OrderLineData> lines) {
}
