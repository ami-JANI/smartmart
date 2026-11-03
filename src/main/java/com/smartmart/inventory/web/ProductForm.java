package com.smartmart.inventory.web;

import java.math.BigDecimal;

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.service.ProductData;
import com.smartmart.inventory.service.ProductSummary;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The product form, with the field rules from the SRS (FR-INV-2). */
@Getter
@Setter
@NoArgsConstructor
public class ProductForm {

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name can have at most 120 characters")
    private String name;

    @NotBlank(message = "Barcode is required")
    @Pattern(regexp = "[0-9]{4,20}", message = "Barcode must be 4 to 20 digits")
    private String barcode;

    @NotNull(message = "Choose a category")
    private Long categoryId;

    @NotNull(message = "Choose piece or weight")
    private SaleUnit saleUnit = SaleUnit.PIECE;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Price can have at most 10 digits and 2 decimals")
    private BigDecimal price;

    @DecimalMin(value = "0", message = "Threshold cannot be negative")
    @Digits(integer = 9, fraction = 3, message = "Threshold can have at most 9 digits and 3 decimals")
    private BigDecimal lowStockThreshold;

    private boolean active = true;

    static ProductForm of(ProductSummary product) {
        ProductForm form = new ProductForm();
        form.name = product.name();
        form.barcode = product.barcode();
        form.categoryId = product.categoryId();
        form.saleUnit = SaleUnit.valueOf(product.saleUnit());
        form.price = product.price();
        form.lowStockThreshold = product.lowStockThreshold();
        form.active = product.active();
        return form;
    }

    ProductData toData() {
        return new ProductData(name, barcode, categoryId, saleUnit, price, lowStockThreshold, active);
    }
}
