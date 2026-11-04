package com.smartmart.purchasing.service;

/** A supplier as the pages show it. */
public record SupplierSummary(Long id, String name, String contactPerson, String phone, String email, String address,
                              boolean active) {
}
