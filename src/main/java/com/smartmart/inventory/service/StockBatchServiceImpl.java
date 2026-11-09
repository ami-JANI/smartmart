package com.smartmart.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.inventory.domain.Product;
import com.smartmart.inventory.domain.StockBatch;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;

/**
 * Manages stock batches and deducts stock in first-expiry-first-out order.
 * Publishes {@link StockChangedEvent} so alerts react via the Observer pattern
 * (GUIDELINES.md section 5.2).
 */
@Service
class StockBatchServiceImpl implements StockBatchService {

    private final StockBatchRepository batches;
    private final ProductRepository products;
    private final ApplicationEventPublisher events;

    @Autowired
    StockBatchServiceImpl(StockBatchRepository batches, ProductRepository products, ApplicationEventPublisher events) {
        this.batches = batches;
        this.products = products;
        this.events = events;
    }

    StockBatchServiceImpl(StockBatchRepository batches, ProductRepository products) {
        this(batches, products, event -> {});
    }

    // Reads the primary, so a batch shows on the product page straight after it is added.
    @Override
    @Transactional
    public List<StockBatchSummary> forProduct(Long productId) {
        LocalDate today = LocalDate.now();
        return batches.findForProduct(productId).stream()
                .map(batch -> new StockBatchSummary(batch.getBatchNumber(), batch.getQuantity(), batch.getCost(),
                        batch.getExpiryDate(), batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)))
                .toList();
    }

    @Override
    @Transactional
    public void add(Long productId, StockBatchData data) {
        Product product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.getSaleUnit().allows(data.quantity())) {
            throw new ProductRuleException("quantity", "A product sold by piece needs a whole number here");
        }
        batches.save(new StockBatch(product, data.batchNumber().strip(), data.quantity(), data.cost(), data.expiryDate()));
        events.publishEvent(new StockChangedEvent(productId));
    }

    @Override
    @Transactional
    public void deduct(Long productId, BigDecimal quantity) {
        Product product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        List<StockBatch> sellable = batches.lockSellable(productId, LocalDate.now());
        BigDecimal available = sellable.stream().map(StockBatch::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(quantity) < 0) {
            throw new InsufficientStockException(product.getName());
        }
        BigDecimal remaining = quantity;
        for (StockBatch batch : sellable) {
            if (remaining.signum() == 0) {
                break;
            }
            remaining = remaining.subtract(batch.take(remaining));
        }
        events.publishEvent(new StockChangedEvent(productId));
    }
}
