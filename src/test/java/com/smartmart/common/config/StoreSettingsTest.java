package com.smartmart.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class StoreSettingsTest {

    @Test
    void settingsArePreserved() {
        StoreSettings settings = new StoreSettings(new BigDecimal("10"), new BigDecimal("15"), 7);
        assertThat(settings.lowStockDefault()).isEqualByComparingTo("10");
        assertThat(settings.vatRate()).isEqualByComparingTo("15");
        assertThat(settings.nearExpiryDaysDefault()).isEqualTo(7);
    }

    @Test
    void customNearExpiryDaysIsPreserved() {
        StoreSettings settings = new StoreSettings(new BigDecimal("5"), new BigDecimal("15"), 14);
        assertThat(settings.nearExpiryDaysDefault()).isEqualTo(14);
    }
}
