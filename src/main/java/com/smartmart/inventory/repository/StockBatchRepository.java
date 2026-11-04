package com.smartmart.inventory.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.smartmart.inventory.domain.StockBatch;

import jakarta.persistence.LockModeType;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    /** One product's total stock. */
    interface ProductTotal {

        Long getProductId();

        BigDecimal getTotal();
    }

    /** Soonest expiry first, batches without an expiry date last (FR-INV-7). */
    @Query("select b from StockBatch b where b.product.id = :productId order by b.expiryDate asc nulls last, b.id asc")
    List<StockBatch> findForProduct(Long productId);

    /**
     * Batches of a product that still hold stock and have not expired before
     * {@code today}, in the order they are sold from (FR-POS-11). The rows are
     * locked until the transaction ends, so two sales cannot take the same stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBatch b where b.product.id = :productId and b.quantity > 0 "
            + "and (b.expiryDate is null or b.expiryDate >= :today) order by b.expiryDate asc nulls last, b.id asc")
    List<StockBatch> lockSellable(Long productId, LocalDate today);

    /** Products without batches are left out of the result. */
    @Query("select b.product.id as productId, sum(b.quantity) as total from StockBatch b "
            + "where b.product.id in :productIds group by b.product.id")
    List<ProductTotal> totalsFor(Collection<Long> productIds);
}
