package com.smartmart.pos.service;

import com.smartmart.pos.domain.DiscountStrategy;

/** A discount that is valid today, with the strategy that works out its amount. */
public record ActiveDiscount(Long id, String name, DiscountStrategy strategy) {
}
