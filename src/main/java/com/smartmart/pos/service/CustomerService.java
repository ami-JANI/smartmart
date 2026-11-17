package com.smartmart.pos.service;

import java.util.List;
import java.util.Optional;

/** Members registered by phone number, so a sale can record its customer. */
public interface CustomerService {

    /**
     * Registers a member.
     *
     * @param name  1 to 100 characters
     * @param phone 6 to 15 digits, optionally starting with +; spaces and dashes are ignored
     * @return the new member's id
     * @throws CustomerRuleException if a value is invalid or the phone number is already registered
     */
    Long register(String name, String phone);

    /** The member with this phone number (spaces and dashes ignored), if any. */
    Optional<CustomerSummary> findByPhone(String phone);

    Optional<CustomerSummary> find(Long id);

    /** Members whose name or phone contains {@code text}, newest first; the latest 50 when text is blank. */
    List<CustomerSummary> search(String text);

    /** The phone number as stored: digits only, keeping a leading +. */
    static String normalisePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String trimmed = phone.strip();
        String digits = trimmed.replaceAll("\\D", "");
        return trimmed.startsWith("+") ? "+" + digits : digits;
    }
}
