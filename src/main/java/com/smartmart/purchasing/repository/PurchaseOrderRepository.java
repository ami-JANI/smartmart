package com.smartmart.purchasing.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartmart.purchasing.domain.PurchaseOrder;
import com.smartmart.purchasing.domain.PurchaseOrderStatus;

import jakarta.persistence.LockModeType;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Page<PurchaseOrder> findAllByOrderByIdDesc(Pageable pageable);

    Page<PurchaseOrder> findByStatusOrderByIdDesc(PurchaseOrderStatus status, Pageable pageable);

    /**
     * Loads an order and locks its row until the transaction ends, so two
     * people acting on the same order at once take turns (FR-PUR-6).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") Long id);
}
