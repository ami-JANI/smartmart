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

class LowStockAlertListenerTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final StockBatchRepository batches = mock(StockBatchRepository.class);
    private final StockAlertRepository alerts = mock(StockAlertRepository.class);
    private final StoreSettings settings = new StoreSettings(new BigDecimal("10"), new BigDecimal("15"), 7);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);

    private LowStockAlertListener listener;

    @BeforeEach
    void setUp() {
        listener = new LowStockAlertListener(products, batches, alerts, settings, clock);
    }

    private Product product(BigDecimal threshold, boolean active) {
        Product p = new Product("Rice", "1234", new Category("Food", null), SaleUnit.KG,
                BigDecimal.TEN, threshold, active);
        ReflectionTestUtils.setField(p, "id", 1L);
        when(products.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void raisesAlertWhenStockBelowThreshold() {
        Product p = product(new BigDecimal("5"), true);
        StockBatch b = new StockBatch(p, "B1", new BigDecimal("4"), BigDecimal.ONE, LocalDate.of(2030, 1, 1));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));
        when(alerts.findByProductIdAndAlertTypeAndStatus(1L, AlertType.LOW_STOCK, AlertStatus.OPEN))
                .thenReturn(Optional.empty());

        listener.onStockChanged(new StockChangedEvent(1L));

        ArgumentCaptor<StockAlert> captor = ArgumentCaptor.forClass(StockAlert.class);
        verify(alerts).save(captor.capture());
        StockAlert alert = captor.getValue();
        assertThat(alert.getAlertType()).isEqualTo(AlertType.LOW_STOCK);
        assertThat(alert.getTotalStock()).isEqualByComparingTo("4");
        assertThat(alert.getThreshold()).isEqualByComparingTo("5");
        assertThat(alert.isOpen()).isTrue();
    }

    @Test
    void raisesAlertWhenStockEqualsThreshold() {
        Product p = product(new BigDecimal("10"), true);
        StockBatch b = new StockBatch(p, "B1", new BigDecimal("10"), BigDecimal.ONE, LocalDate.of(2030, 1, 1));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));
        when(alerts.findByProductIdAndAlertTypeAndStatus(1L, AlertType.LOW_STOCK, AlertStatus.OPEN))
                .thenReturn(Optional.empty());

        listener.onStockChanged(new StockChangedEvent(1L));

        verify(alerts).save(any(StockAlert.class));
    }

    @Test
    void usesStoreDefaultWhenProductHasNoThreshold() {
        Product p = product(null, true);
        StockBatch b = new StockBatch(p, "B1", new BigDecimal("10"), BigDecimal.ONE, LocalDate.of(2030, 1, 1));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));
        when(alerts.findByProductIdAndAlertTypeAndStatus(1L, AlertType.LOW_STOCK, AlertStatus.OPEN))
                .thenReturn(Optional.empty());

        listener.onStockChanged(new StockChangedEvent(1L));

        ArgumentCaptor<StockAlert> captor = ArgumentCaptor.forClass(StockAlert.class);
        verify(alerts).save(captor.capture());
        assertThat(captor.getValue().getThreshold()).isEqualByComparingTo("10"); // store default
    }

    @Test
    void resolvesExistingAlertWhenStockReplenishedAboveThreshold() {
        Product p = product(new BigDecimal("5"), true);
        StockBatch b = new StockBatch(p, "B1", new BigDecimal("8"), BigDecimal.ONE, LocalDate.of(2030, 1, 1));
        when(batches.findForProduct(1L)).thenReturn(List.of(b));

        StockAlert existing = StockAlert.lowStock(p, new BigDecimal("3"), new BigDecimal("5"), Instant.now());
        when(alerts.findByProductIdAndAlertTypeAndStatus(1L, AlertType.LOW_STOCK, AlertStatus.OPEN))
                .thenReturn(Optional.of(existing));

        listener.onStockChanged(new StockChangedEvent(1L));

        assertThat(existing.isOpen()).isFalse();
        verify(alerts, never()).save(any());
    }

    @Test
    void ignoresInactiveProduct() {
        product(new BigDecimal("5"), false);

        listener.onStockChanged(new StockChangedEvent(1L));

        verify(batches, never()).findForProduct(any());
        verify(alerts, never()).save(any());
    }
}
