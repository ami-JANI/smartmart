package com.smartmart.inventory.service;

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
 * by checking whether any batch of the product is close to its expiry date (FR-INV-9).
 * <p>
 * Batches expiring within the store's near-expiry window (default 7 days) raise an alert so
 * inventory staff and managers can take action before stock spoils.
 */
@Component
public class ExpiryAlertListener {

    private final ProductRepository products;
    private final StockBatchRepository batches;
    private final StockAlertRepository alerts;
    private final StoreSettings settings;
    private final Clock clock;

    @Autowired
    public ExpiryAlertListener(ProductRepository products, StockBatchRepository batches,
                               StockAlertRepository alerts, StoreSettings settings) {
        this(products, batches, alerts, settings, Clock.systemUTC());
    }

    public ExpiryAlertListener(ProductRepository products, StockBatchRepository batches,
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
        products.findById(event.productId()).ifPresent(this::checkExpiry);
    }

    public void checkExpiry(Product product) {
        if (!product.isActive()) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate window = today.plusDays(settings.nearExpiryDaysDefault());

        List<StockBatch> productBatches = batches.findForProduct(product.getId());
        for (StockBatch batch : productBatches) {
            String batchNumber = batch.getBatchNumber();
            var existing = alerts.findByProductIdAndAlertTypeAndBatchNumberAndStatus(
                    product.getId(), AlertType.NEAR_EXPIRY, batchNumber, AlertStatus.OPEN);

            boolean isNearExpiry = batch.getQuantity().signum() > 0
                    && batch.getExpiryDate() != null
                    && !batch.getExpiryDate().isBefore(today)
                    && !batch.getExpiryDate().isAfter(window);

            if (isNearExpiry) {
                Instant now = clock.instant();
                if (existing.isPresent()) {
                    existing.get().updateNearExpiry(batch.getQuantity(), batch.getExpiryDate(), now);
                } else {
                    alerts.save(StockAlert.nearExpiry(product, batchNumber, batch.getQuantity(), batch.getExpiryDate(), now));
                }
            } else {
                existing.ifPresent(StockAlert::resolve);
            }
        }
    }
}
