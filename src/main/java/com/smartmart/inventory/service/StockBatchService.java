package com.smartmart.inventory.service;

import java.util.List;

/** Records deliveries of a product (FR-INV-6, FR-INV-7). */
public interface StockBatchService {

    /**
     * Returns a product's batches, always up to date, soonest expiry first and
     * batches without an expiry date last.
     */
    List<StockBatchSummary> forProduct(Long productId);

    /**
     * Records a delivery.
     *
     * @throws ProductNotFoundException if the product does not exist
     * @throws ProductRuleException     if the quantity does not fit the product's sale unit
     */
    void add(Long productId, StockBatchData data);
}
