package com.smartmart.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.pos.service.SalesStatistics;
import com.smartmart.pos.service.SalesStatistics.Breakdown;
import com.smartmart.pos.service.SalesStatistics.ProductSold;

class DailySalesReportServiceImplTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), DHAKA);
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    // Midnight to midnight in Dhaka, which is 18:00 UTC the evening before.
    private static final Instant FROM = Instant.parse("2026-11-09T18:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-10T18:00:00Z");

    private final SalesStatistics sales = mock(SalesStatistics.class);
    private final ProductService products = mock(ProductService.class);
    private final DailySalesReportService service = new DailySalesReportServiceImpl(sales, products, NOW);

    private static ProductSummary product(long id, String name) {
        return new ProductSummary(id, name, "2000000000" + id, 1L, "Grocery", new BigDecimal("10.00"), "PIECE",
                null, true, BigDecimal.TEN, false);
    }

    @BeforeEach
    void catalogue() {
        when(products.search(isNull(), isNull(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product(1, "Rice"), product(2, "Soap"))));
        when(sales.breakdown(any(), any())).thenReturn(
                new Breakdown(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(sales.byProduct(any(), any())).thenReturn(List.of());
    }

    @Test
    void totalsCoverTheWholeDayInTheStoreTimeZone() {
        when(sales.breakdown(FROM, TO)).thenReturn(new Breakdown(3, new BigDecimal("500.00"), new BigDecimal("50.00"),
                new BigDecimal("450.00"), new BigDecimal("58.70")));

        DailySalesReport report = service.report(DAY);

        assertThat(report.day()).isEqualTo(DAY);
        assertThat(report.generatedAt()).isEqualTo(NOW.instant());
        assertThat(report.saleCount()).isEqualTo(3);
        assertThat(report.gross()).isEqualByComparingTo("500.00");
        assertThat(report.discounts()).isEqualByComparingTo("50.00");
        assertThat(report.promotionSavings()).isEqualByComparingTo("0");
        assertThat(report.net()).isEqualByComparingTo("450.00");
        assertThat(report.vatIncluded()).isEqualByComparingTo("58.70");
    }

    @Test
    void productsAreListedLargestAmountFirst() {
        when(sales.byProduct(FROM, TO)).thenReturn(List.of(
                new ProductSold(1L, new BigDecimal("2"), new BigDecimal("100.00")),
                new ProductSold(2L, new BigDecimal("1"), new BigDecimal("400.00")),
                new ProductSold(9L, new BigDecimal("1"), new BigDecimal("50.00"))));

        assertThat(service.report(DAY).lines()).extracting(DailySalesReport.Line::name)
                .containsExactly("Soap", "Rice", "Removed product");
    }

    @Test
    void aDayWithoutSalesIsEmpty() {
        DailySalesReport report = service.report(DAY);

        assertThat(report.saleCount()).isZero();
        assertThat(report.lines()).isEmpty();
    }

    @Test
    void todayUsesTheStoreTimeZone() {
        assertThat(service.today()).isEqualTo(LocalDate.of(2026, 11, 17));
    }
}
