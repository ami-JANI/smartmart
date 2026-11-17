package com.smartmart.pos.service;

import java.time.Instant;

/** A member as the pages show it. */
public record CustomerSummary(Long id, String name, String phone, Instant memberSince) {
}
