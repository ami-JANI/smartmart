package com.smartmart.purchasing.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Manages suppliers (FR-PUR-1 to FR-PUR-3). */
public interface SupplierService {

    /**
     * Finds suppliers. May lag slightly behind recent changes.
     *
     * @param text part of a name, contact person or phone, ignoring case; null or blank for all
     */
    Page<SupplierSummary> search(String text, Pageable pageable);

    /**
     * Returns one supplier, always up to date.
     *
     * @throws SupplierNotFoundException if it does not exist
     */
    SupplierSummary get(Long id);

    /**
     * Adds a supplier.
     *
     * @return the new supplier's id
     * @throws SupplierNameTakenException if another supplier has the name
     */
    Long create(SupplierData data);

    /**
     * Changes a supplier, including making it active or inactive.
     *
     * @throws SupplierNotFoundException  if it does not exist
     * @throws SupplierNameTakenException if another supplier has the name
     */
    void update(Long id, SupplierData data);
}
