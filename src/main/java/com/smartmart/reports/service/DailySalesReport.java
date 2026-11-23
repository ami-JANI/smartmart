package com.smartmart.reports.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One day's sales (SCRUM-37, SCRUM-55). Net is what customers paid: gross less
 * discounts and promotion savings. Prices include VAT, so net includes
 * {@code vatIncluded}. Promotions are on hold, so their savings are 0.
 *
 * @param generatedAt when the report was built; it reads the replica, which can be a second behind
 * @param lines       each product sold, largest amount first; their amounts add up to gross
 */
public record DailySalesReport(LocalDate day, Instant generatedAt, long saleCount, BigDecimal gross,
                               BigDecimal discounts, BigDecimal promotionSavings, BigDecimal net,
                               BigDecimal vatIncluded, List<Line> lines) {

    public record Line(Long productId, String name, String saleUnit, BigDecimal quantity, BigDecimal amount) {
    }
}
