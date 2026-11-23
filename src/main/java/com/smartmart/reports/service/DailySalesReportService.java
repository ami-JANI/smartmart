package com.smartmart.reports.service;

import java.time.LocalDate;

public interface DailySalesReportService {

    /** Today in the store's time zone, the report's default day. */
    LocalDate today();

    DailySalesReport report(LocalDate day);
}
