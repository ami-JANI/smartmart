package com.smartmart.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.inventory.domain.AlertStatus;
import com.smartmart.inventory.domain.AlertType;
import com.smartmart.inventory.domain.Category;
import com.smartmart.inventory.domain.Product;
import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.domain.StockAlert;
import com.smartmart.inventory.repository.StockAlertRepository;

class AlertServiceImplTest {

    private final StockAlertRepository alerts = mock(StockAlertRepository.class);
    private final AlertServiceImpl service = new AlertServiceImpl(alerts);

    private Product product(long id, String name) {
        Product p = new Product(name, "1234", new Category("Food", null), SaleUnit.PIECE,
                BigDecimal.TEN, null, true);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void openAlertsReturnsMappedAlerts() {
        Product p = product(1L, "Rice");
        Instant now = Instant.parse("2026-11-10T00:00:00Z");
        StockAlert low = StockAlert.lowStock(p, new BigDecimal("2"), new BigDecimal("5"), now);
        ReflectionTestUtils.setField(low, "id", 101L);

        StockAlert expiry = StockAlert.nearExpiry(p, "B-1", new BigDecimal("10"), LocalDate.of(2026, 11, 15), now);
        ReflectionTestUtils.setField(expiry, "id", 102L);

        when(alerts.findByStatusOrderByRaisedAtDesc(AlertStatus.OPEN)).thenReturn(List.of(low, expiry));

        List<AlertView> views = service.openAlerts();
        assertThat(views).hasSize(2);

        AlertView v1 = views.get(0);
        assertThat(v1.id()).isEqualTo(101L);
        assertThat(v1.type()).isEqualTo(AlertType.LOW_STOCK);
        assertThat(v1.productId()).isEqualTo(1L);
        assertThat(v1.productName()).isEqualTo("Rice");
        assertThat(v1.totalStock()).isEqualByComparingTo("2");
        assertThat(v1.threshold()).isEqualByComparingTo("5");

        AlertView v2 = views.get(1);
        assertThat(v2.id()).isEqualTo(102L);
        assertThat(v2.type()).isEqualTo(AlertType.NEAR_EXPIRY);
        assertThat(v2.batchNumber()).isEqualTo("B-1");
        assertThat(v2.quantity()).isEqualByComparingTo("10");
        assertThat(v2.expiryDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void openAlertCountDelegatesToRepository() {
        when(alerts.countByStatus(AlertStatus.OPEN)).thenReturn(3L);

        assertThat(service.openAlertCount()).isEqualTo(3L);
    }
}
