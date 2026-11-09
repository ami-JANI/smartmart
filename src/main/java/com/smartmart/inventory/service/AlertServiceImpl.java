package com.smartmart.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.inventory.domain.AlertStatus;
import com.smartmart.inventory.domain.StockAlert;
import com.smartmart.inventory.repository.StockAlertRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class AlertServiceImpl implements AlertService {

    private final StockAlertRepository alerts;

    @Override
    @Transactional(readOnly = true)
    public List<AlertView> openAlerts() {
        return alerts.findByStatusOrderByRaisedAtDesc(AlertStatus.OPEN).stream()
                .map(this::viewOf)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long openAlertCount() {
        return alerts.countByStatus(AlertStatus.OPEN);
    }

    private AlertView viewOf(StockAlert alert) {
        return new AlertView(
                alert.getId(),
                alert.getAlertType(),
                alert.getProduct().getId(),
                alert.getProduct().getName(),
                alert.getRaisedAt(),
                alert.getTotalStock(),
                alert.getThreshold(),
                alert.getBatchNumber(),
                alert.getQuantity(),
                alert.getExpiryDate()
        );
    }
}
