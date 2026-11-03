package com.smartmart.inventory.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.smartmart.inventory.domain.StockBatch;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    /** One product's total stock. */
    interface ProductTotal {

        Long getProductId();

        BigDecimal getTotal();
    }

    /** Soonest expiry first, batches without an expiry date last (FR-INV-7). */
    @Query("select b from StockBatch b where b.product.id = :productId order by b.expiryDate asc nulls last, b.id asc")
    List<StockBatch> findForProduct(Long productId);

    /** Products without batches are left out of the result. */
    @Query("select b.product.id as productId, sum(b.quantity) as total from StockBatch b "
            + "where b.product.id in :productIds group by b.product.id")
    List<ProductTotal> totalsFor(Collection<Long> productIds);
}
