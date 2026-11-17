package com.smartmart.reports.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the manager dashboard shows (reports/dashboard.html). "This month"
 * is the month so far; it is compared with the same days of last month, so a
 * half-finished month is not set against a full one.
 *
 * @param monthChangePercent change of this month's sales against the same days last month, or null
 *                           when last month had no sales in that period
 */
public record Dashboard(Instant generatedAt, LocalDate monthStart, LocalDate today,
                        Money todaySales, Money monthSales, Money previousPeriodSales, BigDecimal monthChangePercent,
                        long memberCount, long openAlerts, long productCount, long lowStockCount, long outOfStockCount,
                        List<DaySales> last30Days, List<ProductRow> topProducts, List<CustomerRow> topCustomers,
                        List<DropRow> droppedProducts, List<StockRow> inventory) {

    /** A number of sales and their value. */
    public record Money(long count, BigDecimal amount) {
    }

    public record DaySales(LocalDate day, BigDecimal amount) {
    }

    public record ProductRow(Long productId, String name, String saleUnit, BigDecimal quantity, BigDecimal amount) {
    }

    public record CustomerRow(String name, String phone, long sales, BigDecimal amount) {
    }

    /** A product that sold less this month than in the same days last month. */
    public record DropRow(Long productId, String name, String saleUnit, BigDecimal previousQuantity,
                          BigDecimal currentQuantity, BigDecimal changePercent) {
    }

    public enum StockStatus { OUT, LOW, OK }

    public record StockRow(Long productId, String name, String categoryName, String saleUnit, BigDecimal totalStock,
                           StockStatus status) {
    }
}
