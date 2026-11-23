package com.smartmart.reports.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.pos.service.SalesStatistics;
import com.smartmart.reports.service.DailySalesReport.Line;

/** Builds the report from the pos and inventory public services only (GUIDELINES 3.4). */
@Service
class DailySalesReportServiceImpl implements DailySalesReportService {

    private static final int ALL_PRODUCTS = 1000;

    private final SalesStatistics sales;
    private final ProductService products;
    private final Clock clock;

    @Autowired
    DailySalesReportServiceImpl(SalesStatistics sales, ProductService products) {
        this(sales, products, Clock.systemDefaultZone());
    }

    DailySalesReportServiceImpl(SalesStatistics sales, ProductService products, Clock clock) {
        this.sales = sales;
        this.products = products;
        this.clock = clock;
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Override
    public DailySalesReport report(LocalDate day) {
        Instant from = day.atStartOfDay(clock.getZone()).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        SalesStatistics.Breakdown totals = sales.breakdown(from, to);
        return new DailySalesReport(day, clock.instant(), totals.count(), totals.gross(), totals.discounts(),
                BigDecimal.ZERO, totals.net(), totals.vat(), lines(sales.byProduct(from, to)));
    }

    private List<Line> lines(List<SalesStatistics.ProductSold> sold) {
        Map<Long, ProductSummary> catalogue = products.search(null, null, false,
                        PageRequest.of(0, ALL_PRODUCTS, Sort.by("name"))).getContent().stream()
                .collect(Collectors.toMap(ProductSummary::id, Function.identity()));
        return sold.stream()
                .map(row -> {
                    ProductSummary product = catalogue.get(row.productId());
                    return new Line(row.productId(), product == null ? "Removed product" : product.name(),
                            product == null ? "PIECE" : product.saleUnit(), row.quantity(), row.amount());
                })
                .sorted(Comparator.comparing(Line::amount).reversed().thenComparing(Line::name))
                .toList();
    }
}
