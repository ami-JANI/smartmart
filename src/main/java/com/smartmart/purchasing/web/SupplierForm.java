package com.smartmart.purchasing.web;

import com.smartmart.purchasing.service.SupplierData;
import com.smartmart.purchasing.service.SupplierSummary;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The supplier form, with the field rules from the SRS (FR-PUR-1). */
@Getter
@Setter
@NoArgsConstructor
public class SupplierForm {

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name can have at most 120 characters")
    private String name;

    @Size(max = 100, message = "Contact person can have at most 100 characters")
    private String contactPerson;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "[0-9+\\- ]{6,20}", message = "Phone must be 6 to 20 digits, spaces, + or -")
    private String phone;

    @Email(message = "Enter a valid email address")
    @Size(max = 120, message = "Email can have at most 120 characters")
    private String email;

    @Size(max = 255, message = "Address can have at most 255 characters")
    private String address;

    private boolean active = true;

    static SupplierForm of(SupplierSummary supplier) {
        SupplierForm form = new SupplierForm();
        form.name = supplier.name();
        form.contactPerson = supplier.contactPerson();
        form.phone = supplier.phone();
        form.email = supplier.email();
        form.address = supplier.address();
        form.active = supplier.active();
        return form;
    }

    SupplierData toData() {
        return new SupplierData(name, contactPerson, phone, email, address, active);
    }
}
