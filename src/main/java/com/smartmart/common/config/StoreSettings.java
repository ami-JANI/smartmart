package com.smartmart.common.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Store-wide settings, read once at startup. Uses the Singleton pattern through
 * Spring: one immutable bean per application (GUIDELINES.md section 5.4).
 *
 * @param lowStockDefault low-stock threshold for products without their own
 */
@ConfigurationProperties("smartmart.store")
public record StoreSettings(BigDecimal lowStockDefault) {
}
