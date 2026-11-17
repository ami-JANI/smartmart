package com.smartmart.reports.service;

/** The manager dashboard: sales this month, best sellers, best customers, drops and live stock. */
public interface DashboardService {

    /** Builds the dashboard as of now. Sales come from the replica and can be a second behind. */
    Dashboard dashboard();
}
