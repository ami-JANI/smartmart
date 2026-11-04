package com.smartmart.auth.service;

import java.util.Set;

import com.smartmart.auth.domain.RoleName;

/** What an administrator enters for a user. {@code password} is blank on an edit to keep the old one. */
public record UserData(String username, String fullName, String password, Set<RoleName> roles) {
}
