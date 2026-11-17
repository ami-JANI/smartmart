package com.smartmart.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.smartmart.inventory.service.AlertService;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.pos.service.SalesStatistics;
import com.smartmart.pos.service.SalesStatistics.ProductSold;
import com.smartmart.pos.service.SalesStatistics.Total;
import com.smartmart.reports.service.Dashboard.StockStatus;

class DashboardServiceImplTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    // 17 Nov 2026, 15:00 in Dhaka.
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), DHAKA);
    private static final Instant MONTH_START = LocalDate.of(2026, 11, 1).atStartOfDay(DHAKA).toInstant();
    private static final Instant PREVIOUS_START = LocalDate.of(2026, 10, 1).atStartOfDay(DHAKA).toInstant();
    private static final Instant PREVIOUS_END = Instant.parse("2026-10-17T09:00:00Z");

    private final SalesStatistics sales = mock(SalesStatistics.class);
    private final ProductService products = mock(ProductService.class);
    private final AlertService alerts = mock(AlertService.class);
    private final DashboardService service = new DashboardServiceImpl(sales, products, alerts, NOW);

    private static ProductSummary product(long id, String name, String stock, boolean low) {
        return new ProductSummary(id, name, "2000000000" + id, 1L, "Grocery", new BigDecimal("10.00"), "PIECE",
                null, true, new BigDecimal(stock), low);
    }

    private static ProductSold sold(long id, String quantity, String amount) {
        return new ProductSold(id, new BigDecimal(quantity), new BigDecimal(amount));
    }

    @BeforeEach
    void catalogueAndTotals() {
        when(products.search(isNull(), isNull(), eq(false), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                product(1, "Rice", "40", false), product(2, "Cola", "0", true), product(3, "Soap", "6", true))));
        when(sales.total(any(), any())).thenReturn(new Total(0, BigDecimal.ZERO));
        when(sales.total(MONTH_START, NOW.instant())).thenReturn(new Total(10, new BigDecimal("1200.00")));
        when(sales.total(PREVIOUS_START, PREVIOUS_END)).thenReturn(new Total(8, new BigDecimal("1000.00")));
        when(sales.byDay(any(), any(), any())).thenReturn(List.of());
        when(sales.byProduct(MONTH_START, NOW.instant())).thenReturn(List.of(sold(1, "30", "300"), sold(3, "2", "110")));
        when(sales.byProduct(PREVIOUS_START, PREVIOUS_END)).thenReturn(List.of(sold(1, "20", "200"), sold(2, "40", "1600"), sold(3, "4", "220")));
        when(sales.topCustomers(any(), any(), anyInt())).thenReturn(List.of());
        when(sales.memberCount()).thenReturn(6L);
        when(alerts.openAlertCount()).thenReturn(4L);
    }

    @Test
    void thisMonthIsComparedWithTheSameDaysLastMonth() {
        Dashboard dashboard = service.dashboard();

        assertThat(dashboard.monthSales().amount()).isEqualByComparingTo("1200.00");
        assertThat(dashboard.previousPeriodSales().amount()).isEqualByComparingTo("1000.00");
        assertThat(dashboard.monthChangePercent()).isEqualByComparingTo("20.0");
        assertThat(dashboard.monthStart()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void droppedProductsAreThoseSellingLessThanLastMonthBiggestDropFirst() {
        Dashboard dashboard = service.dashboard();

        assertThat(dashboard.droppedProducts()).extracting(Dashboard.DropRow::name).containsExactly("Cola", "Soap");
        assertThat(dashboard.droppedProducts().get(0).changePercent()).isEqualByComparingTo("-100.0");
        assertThat(dashboard.droppedProducts().get(1).changePercent()).isEqualByComparingTo("-50.0");
    }

    @Test
    void bestSellersAreOrderedByValue() {
        assertThat(service.dashboard().topProducts()).extracting(Dashboard.ProductRow::name).containsExactly("Rice", "Soap");
    }

    @Test
    void inventoryShowsOutOfStockFirstThenLow() {
        Dashboard dashboard = service.dashboard();

        assertThat(dashboard.inventory()).extracting(Dashboard.StockRow::status)
                .containsExactly(StockStatus.OUT, StockStatus.LOW, StockStatus.OK);
        assertThat(dashboard.outOfStockCount()).isEqualTo(1);
        assertThat(dashboard.lowStockCount()).isEqualTo(1);
        assertThat(dashboard.openAlerts()).isEqualTo(4);
        assertThat(dashboard.memberCount()).isEqualTo(6);
    }

    @Test
    void chartHasEveryOneOfTheLast30Days() {
        when(sales.byDay(any(), any(), any())).thenReturn(List.of(
                new SalesStatistics.Day(LocalDate.of(2026, 11, 17), 2, new BigDecimal("55.00"))));

        List<Dashboard.DaySales> days = service.dashboard().last30Days();

        assertThat(days).hasSize(30);
        assertThat(days.get(0).day()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(days.get(29).amount()).isEqualByComparingTo("55.00");
        assertThat(days.get(28).amount()).isEqualByComparingTo("0");
    }

    @Test
    void percentChangeIsNullWithoutAnEarlierFigure() {
        assertThat(DashboardServiceImpl.percentChange(BigDecimal.ZERO, BigDecimal.TEN)).isNull();
        assertThat(DashboardServiceImpl.percentChange(new BigDecimal("3"), new BigDecimal("2"))).isEqualByComparingTo("-33.3");
    }
}
