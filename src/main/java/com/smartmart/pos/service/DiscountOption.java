package com.smartmart.pos.service;

/** A discount the cashier can pick on the cart screen, for example "Staff" with "10% off". */
public record DiscountOption(Long id, String name, String description) {
}
