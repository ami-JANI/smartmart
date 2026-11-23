package com.smartmart.pos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Totals of completed sales, for the dashboard and reports (reports module). Reads
 * the replica, so a sale made in the last second may be missing (GUIDELINES 8.2).
 * Every period is [from, to).
 */
public interface SalesStatistics {

    Total total(Instant from, Instant to);

    /** Gross, discounts, net and VAT of the sales in the period. */
    Breakdown breakdown(Instant from, Instant to);

    /** One entry per day that had sales, oldest first. */
    List<Day> byDay(Instant from, Instant to, ZoneId zone);

    /** One entry per product sold, in no particular order. */
    List<ProductSold> byProduct(Instant from, Instant to);

    /** The members who bought the most, by value. */
    List<TopCustomer> topCustomers(Instant from, Instant to, int limit);

    long memberCount();

    record Total(long count, BigDecimal amount) {
    }

    record Breakdown(long count, BigDecimal gross, BigDecimal discounts, BigDecimal net, BigDecimal vat) {
    }

    record Day(LocalDate day, long count, BigDecimal amount) {
    }

    record ProductSold(Long productId, BigDecimal quantity, BigDecimal amount) {
    }

    record TopCustomer(Long customerId, String name, String phone, long sales, BigDecimal amount) {
    }
}
