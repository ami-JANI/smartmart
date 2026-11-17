package com.smartmart.reports.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.smartmart.inventory.service.AlertService;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.pos.service.SalesStatistics;
import com.smartmart.reports.service.Dashboard.CustomerRow;
import com.smartmart.reports.service.Dashboard.DaySales;
import com.smartmart.reports.service.Dashboard.DropRow;
import com.smartmart.reports.service.Dashboard.Money;
import com.smartmart.reports.service.Dashboard.ProductRow;
import com.smartmart.reports.service.Dashboard.StockRow;
import com.smartmart.reports.service.Dashboard.StockStatus;

/**
 * Builds the dashboard from the other modules' public services only: sales
 * totals from pos, products and stock from inventory (GUIDELINES 3.4).
 */
@Service
class DashboardServiceImpl implements DashboardService {

    private static final int TOP = 5;
    private static final int ALL_PRODUCTS = 1000;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SalesStatistics sales;
    private final ProductService products;
    private final AlertService alerts;
    private final Clock clock;

    @Autowired
    DashboardServiceImpl(SalesStatistics sales, ProductService products, AlertService alerts) {
        this(sales, products, alerts, Clock.systemDefaultZone());
    }

    DashboardServiceImpl(SalesStatistics sales, ProductService products, AlertService alerts, Clock clock) {
        this.sales = sales;
        this.products = products;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Override
    public Dashboard dashboard() {
        ZoneId zone = clock.getZone();
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        Instant todayStart = start(today, zone);
        Instant monthFrom = start(monthStart, zone);
        Instant previousFrom = start(monthStart.minusMonths(1), zone);
        // The same days last month, never running into this month.
        Instant previousTo = minInstant(ZonedDateTime.ofInstant(now, zone).minusMonths(1).toInstant(), monthFrom);

        Money todaySales = money(sales.total(todayStart, now));
        Money monthSales = money(sales.total(monthFrom, now));
        Money previousSales = money(sales.total(previousFrom, previousTo));

        Map<Long, ProductSummary> catalogue = products.search(null, null, false,
                        PageRequest.of(0, ALL_PRODUCTS, Sort.by("name"))).getContent().stream()
                .collect(Collectors.toMap(ProductSummary::id, Function.identity()));
        List<SalesStatistics.ProductSold> thisMonth = sales.byProduct(monthFrom, now);
        List<SalesStatistics.ProductSold> lastMonth = sales.byProduct(previousFrom, previousTo);
        List<StockRow> inventory = inventory(catalogue);

        return new Dashboard(now, monthStart, today, todaySales, monthSales, previousSales,
                percentChange(previousSales.amount(), monthSales.amount()),
                sales.memberCount(), alerts.openAlertCount(), catalogue.size(),
                inventory.stream().filter(row -> row.status() == StockStatus.LOW).count(),
                inventory.stream().filter(row -> row.status() == StockStatus.OUT).count(),
                last30Days(today, zone, now), topProducts(thisMonth, catalogue),
                sales.topCustomers(monthFrom, now, TOP).stream()
                        .map(c -> new CustomerRow(c.name(), c.phone(), c.sales(), c.amount())).toList(),
                drops(lastMonth, thisMonth, catalogue), inventory);
    }

    // Every one of the last 30 days, with 0 for days without sales, so the chart has no gaps.
    private List<DaySales> last30Days(LocalDate today, ZoneId zone, Instant now) {
        LocalDate first = today.minusDays(29);
        Map<LocalDate, BigDecimal> byDay = sales.byDay(start(first, zone), now, zone).stream()
                .collect(Collectors.toMap(SalesStatistics.Day::day, SalesStatistics.Day::amount));
        return first.datesUntil(today.plusDays(1))
                .map(day -> new DaySales(day, byDay.getOrDefault(day, BigDecimal.ZERO)))
                .toList();
    }

    private static List<ProductRow> topProducts(List<SalesStatistics.ProductSold> sold, Map<Long, ProductSummary> catalogue) {
        return sold.stream()
                .sorted(Comparator.comparing(SalesStatistics.ProductSold::amount).reversed())
                .limit(TOP)
                .map(row -> {
                    ProductSummary product = catalogue.get(row.productId());
                    return new ProductRow(row.productId(), nameOf(product), unitOf(product), row.quantity(), row.amount());
                })
                .toList();
    }

    private static List<DropRow> drops(List<SalesStatistics.ProductSold> before, List<SalesStatistics.ProductSold> now,
                                       Map<Long, ProductSummary> catalogue) {
        Map<Long, BigDecimal> nowByProduct = now.stream()
                .collect(Collectors.toMap(SalesStatistics.ProductSold::productId, SalesStatistics.ProductSold::quantity));
        return before.stream()
                .filter(row -> row.quantity().signum() > 0)
                .map(row -> {
                    BigDecimal current = nowByProduct.getOrDefault(row.productId(), BigDecimal.ZERO);
                    ProductSummary product = catalogue.get(row.productId());
                    return new DropRow(row.productId(), nameOf(product), unitOf(product), row.quantity(), current,
                            percentChange(row.quantity(), current));
                })
                .filter(row -> row.currentQuantity().compareTo(row.previousQuantity()) < 0)
                .sorted(Comparator.comparing(DropRow::changePercent))
                .limit(TOP)
                .toList();
    }

    // Out of stock first, then low, then the rest, each by name.
    private static List<StockRow> inventory(Map<Long, ProductSummary> catalogue) {
        return catalogue.values().stream()
                .filter(ProductSummary::active)
                .map(p -> new StockRow(p.id(), p.name(), p.categoryName(), p.saleUnit(), p.totalStock(), statusOf(p)))
                .sorted(Comparator.comparing(StockRow::status).thenComparing(StockRow::name))
                .toList();
    }

    private static StockStatus statusOf(ProductSummary product) {
        if (product.totalStock().signum() <= 0) {
            return StockStatus.OUT;
        }
        return product.lowStock() ? StockStatus.LOW : StockStatus.OK;
    }

    /** (after - before) / before × 100, rounded to 1 decimal; null when before is 0. */
    static BigDecimal percentChange(BigDecimal before, BigDecimal after) {
        if (before.signum() == 0) {
            return null;
        }
        return after.subtract(before).multiply(HUNDRED).divide(before, 1, RoundingMode.HALF_UP);
    }

    private static Money money(SalesStatistics.Total total) {
        return new Money(total.count(), total.amount());
    }

    private static Instant start(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }

    private static Instant minInstant(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static String nameOf(ProductSummary product) {
        return product == null ? "Removed product" : product.name();
    }

    private static String unitOf(ProductSummary product) {
        return product == null ? "PIECE" : product.saleUnit();
    }
}
