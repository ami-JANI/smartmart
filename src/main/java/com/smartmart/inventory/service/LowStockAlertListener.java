package com.smartmart.inventory.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.common.config.StoreSettings;
import com.smartmart.inventory.domain.AlertStatus;
import com.smartmart.inventory.domain.AlertType;
import com.smartmart.inventory.domain.Product;
import com.smartmart.inventory.domain.StockAlert;
import com.smartmart.inventory.domain.StockBatch;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockAlertRepository;
import com.smartmart.inventory.repository.StockBatchRepository;

/**
 * Observer pattern (SEPM Lec 2, GUIDELINES.md section 5.2): reacts to {@link StockChangedEvent}
 * by checking whether the product's total unexpired stock has fallen to or below its threshold
 * (FR-INV-8, FR-INV-9).
 * <p>
 * The publisher holds no reference to this observer, keeping inventory loosely coupled.
 */
@Component
public class LowStockAlertListener {

    private final ProductRepository products;
    private final StockBatchRepository batches;
    private final StockAlertRepository alerts;
    private final StoreSettings settings;
    private final Clock clock;

    @Autowired
    public LowStockAlertListener(ProductRepository products, StockBatchRepository batches,
                                 StockAlertRepository alerts, StoreSettings settings) {
        this(products, batches, alerts, settings, Clock.systemUTC());
    }

    public LowStockAlertListener(ProductRepository products, StockBatchRepository batches,
                                 StockAlertRepository alerts, StoreSettings settings, Clock clock) {
        this.products = products;
        this.batches = batches;
        this.alerts = alerts;
        this.settings = settings;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onStockChanged(StockChangedEvent event) {
        products.findById(event.productId()).ifPresent(this::checkStock);
    }

    public void checkStock(Product product) {
        if (!product.isActive()) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        List<StockBatch> unexpired = batches.findForProduct(product.getId()).stream()
                .filter(b -> b.getExpiryDate() == null || !b.getExpiryDate().isBefore(today))
                .toList();
        BigDecimal totalStock = unexpired.stream()
                .map(StockBatch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal threshold = product.getLowStockThreshold() != null
                ? product.getLowStockThreshold()
                : settings.lowStockDefault();

        var existing = alerts.findByProductIdAndAlertTypeAndStatus(product.getId(), AlertType.LOW_STOCK, AlertStatus.OPEN);

        if (totalStock.compareTo(threshold) <= 0) {
            Instant now = clock.instant();
            if (existing.isPresent()) {
                existing.get().updateLowStock(totalStock, threshold, now);
            } else {
                alerts.save(StockAlert.lowStock(product, totalStock, threshold, now));
            }
        } else {
            existing.ifPresent(StockAlert::resolve);
        }
    }
}
