package com.smartmart.inventory.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Manages products (FR-INV-2 to FR-INV-5 and FR-INV-8). */
public interface ProductService {

    /**
     * Finds products. May lag slightly behind recent changes.
     *
     * @param text       part of a name or barcode, ignoring case; null or blank for all
     * @param categoryId products directly in this category; null for all
     * @param lowStockOnly true to keep only products that are low on stock
     */
    Page<ProductSummary> search(String text, Long categoryId, boolean lowStockOnly, Pageable pageable);

    /**
     * Returns one product, always up to date.
     *
     * @throws ProductNotFoundException if it does not exist
     */
    ProductSummary get(Long id);

    /**
     * Adds a product.
     *
     * @return the new product's id
     * @throws ProductRuleException if the barcode is taken, the category does not exist,
     *                              or the threshold does not fit the sale unit
     */
    Long create(ProductData data);

    /**
     * Changes a product. A new price affects only later sales.
     *
     * @throws ProductNotFoundException if it does not exist
     * @throws ProductRuleException     for the same reasons as {@link #create}
     */
    void update(Long id, ProductData data);
}
