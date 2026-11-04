package com.smartmart.purchasing.service;

/** What a person enters for a supplier. Contact person, email and address may be null or blank. */
public record SupplierData(String name, String contactPerson, String phone, String email, String address,
                           boolean active) {
}
