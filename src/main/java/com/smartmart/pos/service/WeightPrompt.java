package com.smartmart.pos.service;

/** A weighed product was scanned without a weight, so the cashier must enter one (FR-POS-2). */
public record WeightPrompt(String barcode, String productName) {
}
