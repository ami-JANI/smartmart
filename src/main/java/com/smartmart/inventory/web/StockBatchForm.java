package com.smartmart.inventory.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.smartmart.inventory.service.StockBatchData;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The "add a batch" form on the product page (FR-INV-6). */
@Getter
@Setter
@NoArgsConstructor
public class StockBatchForm {

    @NotBlank(message = "Batch number is required")
    @Size(max = 50, message = "Batch number can have at most 50 characters")
    private String batchNumber;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than 0")
    @Digits(integer = 9, fraction = 3, message = "Quantity can have at most 9 digits and 3 decimals")
    private BigDecimal quantity;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0", message = "Cost cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Cost can have at most 10 digits and 2 decimals")
    private BigDecimal cost;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;

    StockBatchData toData() {
        return new StockBatchData(batchNumber, quantity, cost, expiryDate);
    }
}
