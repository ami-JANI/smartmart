package com.smartmart.auth.service;

import java.util.List;

/** A user as the pages show it. {@code roles} are role names such as CASHIER, in role order. */
public record UserSummary(Long id, String username, String fullName, List<String> roles, boolean active) {
}
