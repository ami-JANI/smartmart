package com.smartmart.inventory.service;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * The number of open stock alerts, low stock and near expiry together, as the
 * gauge {@code smartmart.stock.alerts.open} (SCRUM-60). It is read each time
 * Prometheus scrapes, so it is always current.
 */
@Component
class AlertMetrics implements MeterBinder {

    private final AlertService alerts;

    AlertMetrics(AlertService alerts) {
        this.alerts = alerts;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("smartmart.stock.alerts.open", alerts, AlertService::openAlertCount)
                .description("Open low-stock and near-expiry alerts")
                .register(registry);
    }
}
