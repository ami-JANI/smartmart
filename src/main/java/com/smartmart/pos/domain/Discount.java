package com.smartmart.pos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A discount a manager defined: a percentage or a flat amount off the sale,
 * valid from {@code startsOn} to {@code endsOn} inclusive (FR-POS-6). The
 * cashier picks one; the amount comes from {@link #strategy()}.
 */
@Entity
@Table(name = "discounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Discount(String name, DiscountType type, BigDecimal value, LocalDate startsOn, LocalDate endsOn) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    public DiscountStrategy strategy() {
        return type.strategyFor(value);
    }

    public String description() {
        return type.describe(value);
    }
}
