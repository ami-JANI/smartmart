package com.smartmart.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.common.config.StoreSettings;
import com.smartmart.inventory.domain.AlertStatus;
import com.smartmart.inventory.domain.AlertType;
import com.smartmart.inventory.domain.Category;
import com.smartmart.inventory.domain.Product;
import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.domain.StockAlert;
import com.smartmart.inventory.domain.StockBatch;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockAlertRepository;
import com.smartmart.inventory.repository.StockBatchRepository;

class ExpiryAlertListenerTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final StockBatchRepository batches = mock(StockBatchRepository.class);
    private final StockAlertRepository alerts = mock(StockAlertRepository.class);
    private final StoreSettings settings = new StoreSettings(new BigDecimal("10"), new BigDecimal("15"), 7);
    private final LocalDate today = LocalDate.of(2026, 11, 10);
    private final Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private ExpiryAlertListener listener;

    @BeforeEach
    void setUp() {
        listener = new ExpiryAlertListener(products, batches, alerts, settings, clock);
    }

    private Product product() {
        Product p = new Product("Milk", "5678", new Category("Dairy", null), SaleUnit.PIECE,
                BigDecimal.TEN, null, true);
        ReflectionTestUtils.setField(p, "id", 1L);
        when(products.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void raisesAlertWhenBatchExpiresWithinWindow() {
        Product p = product();
        StockBatch b = new StockBatch(p, "B-NEAR", new BigDecimal("5"), BigDecimal.ONE, today.plusDays(3));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));
        when(alerts.findByProductIdAndAlertTypeAndBatchNumberAndStatus(1L, AlertType.NEAR_EXPIRY, "B-NEAR", AlertStatus.OPEN))
                .thenReturn(Optional.empty());

        listener.onStockChanged(new StockChangedEvent(1L));

        ArgumentCaptor<StockAlert> captor = ArgumentCaptor.forClass(StockAlert.class);
        verify(alerts).save(captor.capture());
        StockAlert alert = captor.getValue();
        assertThat(alert.getAlertType()).isEqualTo(AlertType.NEAR_EXPIRY);
        assertThat(alert.getBatchNumber()).isEqualTo("B-NEAR");
        assertThat(alert.getQuantity()).isEqualByComparingTo("5");
        assertThat(alert.getExpiryDate()).isEqualTo(today.plusDays(3));
        assertThat(alert.isOpen()).isTrue();
    }

    @Test
    void raisesAlertWhenBatchExpiresAtExactWindowBoundary() {
        Product p = product();
        StockBatch b = new StockBatch(p, "B-BOUNDARY", new BigDecimal("2"), BigDecimal.ONE, today.plusDays(7));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));
        when(alerts.findByProductIdAndAlertTypeAndBatchNumberAndStatus(1L, AlertType.NEAR_EXPIRY, "B-BOUNDARY", AlertStatus.OPEN))
                .thenReturn(Optional.empty());

        listener.onStockChanged(new StockChangedEvent(1L));

        verify(alerts).save(any(StockAlert.class));
    }

    @Test
    void ignoresBatchExpiringBeyondWindow() {
        Product p = product();
        StockBatch b = new StockBatch(p, "B-FAR", new BigDecimal("10"), BigDecimal.ONE, today.plusDays(8));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));

        listener.onStockChanged(new StockChangedEvent(1L));

        verify(alerts, never()).save(any());
    }

    @Test
    void ignoresAlreadyExpiredBatch() {
        Product p = product();
        StockBatch b = new StockBatch(p, "B-EXPIRED", new BigDecimal("10"), BigDecimal.ONE, today.minusDays(1));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));

        listener.onStockChanged(new StockChangedEvent(1L));

        verify(alerts, never()).save(any());
    }

    @Test
    void ignoresBatchWithoutExpiryDate() {
        Product p = product();
        StockBatch b = new StockBatch(p, "B-NO-EXP", new BigDecimal("10"), BigDecimal.ONE, null);
        when(batches.findForProduct(1L)).thenReturn(List.of(b));

        listener.onStockChanged(new StockChangedEvent(1L));

        verify(alerts, never()).save(any());
    }

    @Test
    void resolvesExistingAlertWhenBatchQuantityReachesZero() {
        Product p = product();
        StockBatch b = new StockBatch(p, "B-ZERO", BigDecimal.ZERO, BigDecimal.ONE, today.plusDays(2));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));

        StockAlert existing = StockAlert.nearExpiry(p, "B-ZERO", new BigDecimal("5"), today.plusDays(2), Instant.now());
        when(alerts.findByProductIdAndAlertTypeAndBatchNumberAndStatus(1L, AlertType.NEAR_EXPIRY, "B-ZERO", AlertStatus.OPEN))
                .thenReturn(Optional.of(existing));

        listener.onStockChanged(new StockChangedEvent(1L));

        assertThat(existing.isOpen()).isFalse();
        verify(alerts, never()).save(any());
    }
}
