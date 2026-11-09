package com.smartmart.inventory.service;

/**
 * Published whenever stock changes (a batch added or deducted).
 * Handled by alert listeners using the Observer pattern (GUIDELINES.md section 5.2).
 */
public record StockChangedEvent(Long productId) {
}
