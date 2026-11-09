package com.smartmart.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.smartmart.inventory.domain.AlertStatus;
import com.smartmart.inventory.domain.AlertType;
import com.smartmart.inventory.domain.StockAlert;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    long countByStatus(AlertStatus status);

    @Query("select a from StockAlert a join fetch a.product where a.status = :status order by a.raisedAt desc")
    List<StockAlert> findByStatusOrderByRaisedAtDesc(AlertStatus status);

    Optional<StockAlert> findByProductIdAndAlertTypeAndStatus(Long productId, AlertType alertType, AlertStatus status);

    Optional<StockAlert> findByProductIdAndAlertTypeAndBatchNumberAndStatus(Long productId, AlertType alertType,
                                                                            String batchNumber, AlertStatus status);

    List<StockAlert> findByProductIdAndAlertType(Long productId, AlertType alertType);
}
