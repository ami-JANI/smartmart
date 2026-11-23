package com.smartmart.pos.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.pos.repository.CustomerRepository;
import com.smartmart.pos.repository.SaleRepository;

import lombok.RequiredArgsConstructor;

// Read-only, so the dashboard's queries run on the replica (GUIDELINES 8.2).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class SalesStatisticsImpl implements SalesStatistics {

    private final SaleRepository sales;
    private final CustomerRepository customers;

    @Override
    public Total total(Instant from, Instant to) {
        SaleRepository.Totals totals = sales.totals(from, to);
        return new Total(totals.getCount(), totals.getAmount());
    }

    @Override
    public Breakdown breakdown(Instant from, Instant to) {
        SaleRepository.Breakdown row = sales.breakdown(from, to);
        return new Breakdown(row.getCount(), row.getGross(), row.getDiscounts(), row.getNet(), row.getVat());
    }

    @Override
    public List<Day> byDay(Instant from, Instant to, ZoneId zone) {
        return sales.totalsByDay(from, to, zone.getId()).stream()
                .map(row -> new Day(LocalDate.parse(row.getDay()), row.getCount(), row.getAmount()))
                .toList();
    }

    @Override
    public List<ProductSold> byProduct(Instant from, Instant to) {
        return sales.totalsByProduct(from, to).stream()
                .map(row -> new ProductSold(row.getProductId(), row.getQuantity(), row.getAmount()))
                .toList();
    }

    @Override
    public List<TopCustomer> topCustomers(Instant from, Instant to, int limit) {
        return sales.topCustomers(from, to, limit).stream()
                .map(row -> new TopCustomer(row.getCustomerId(), row.getName(), row.getPhone(), row.getCount(),
                        row.getAmount()))
                .toList();
    }

    @Override
    public long memberCount() {
        return customers.count();
    }
}
