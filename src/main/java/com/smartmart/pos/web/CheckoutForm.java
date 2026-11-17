package com.smartmart.pos.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The cash payment form on pos/checkout.html (FR-POS-9). */
@Getter
@Setter
@NoArgsConstructor
public class CheckoutForm {

    @NotNull(message = "Enter the cash received")
    @DecimalMin(value = "0", message = "Cash received cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Enter an amount with at most 2 decimals")
    private BigDecimal cashReceived;
}
