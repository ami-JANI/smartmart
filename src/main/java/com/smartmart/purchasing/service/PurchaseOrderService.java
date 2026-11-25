package com.smartmart.purchasing.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartmart.purchasing.domain.IllegalStateTransitionException;
import com.smartmart.purchasing.domain.PurchaseOrderRuleException;
import com.smartmart.purchasing.domain.PurchaseOrderStatus;

/**
 * Raises purchase orders and moves them through their states (FR-PUR-4 to
 * FR-PUR-8). Every action reads and locks the order on the primary, so it
 * always acts on the latest status.
 */
public interface PurchaseOrderService {

    /**
     * Lists orders, newest first. May lag slightly behind recent changes.
     *
     * @param status only orders in this status; null for all
     */
    Page<PurchaseOrderSummary> search(PurchaseOrderStatus status, Pageable pageable);

    /**
     * Returns one order with its lines, always up to date.
     *
     * @throws PurchaseOrderNotFoundException if it does not exist
     */
    PurchaseOrderDetail get(Long id);

    /**
     * Raises a draft order.
     *
     * @return the new order's id
     * @throws SupplierNotFoundException  if the supplier does not exist
     * @throws com.smartmart.inventory.service.ProductNotFoundException if a product does not exist
     * @throws PurchaseOrderRuleException if the supplier or a product is inactive, there are no
     *                                    lines, a product is listed twice, or a quantity or cost
     *                                    breaks the rules in the SRS lines table
     */
    Long create(PurchaseOrderData data);

    /**
     * Approves a draft.
     *
     * @throws PurchaseOrderNotFoundException  if it does not exist
     * @throws IllegalStateTransitionException if it is not a draft
     */
    void submit(Long id);

    /**
     * Cancels a draft or submitted order.
     *
     * @throws PurchaseOrderNotFoundException  if it does not exist
     * @throws IllegalStateTransitionException in any other status
     */
    void cancel(Long id);

    /**
     * Closes a received or partially received order.
     *
     * @throws PurchaseOrderNotFoundException  if it does not exist
     * @throws IllegalStateTransitionException in any other status
     */
    void close(Long id);
}
