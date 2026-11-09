package com.smartmart.inventory.service;

import java.util.List;

/**
 * Reads inventory alerts for the dashboard and the alerts page (FR-INV-8, FR-INV-9).
 */
public interface AlertService {

    List<AlertView> openAlerts();

    long openAlertCount();
}
