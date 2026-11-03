package com.smartmart.inventory.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The "add a category" form. {@code parentId} is null for the top level. */
@Getter
@Setter
@NoArgsConstructor
public class CategoryForm {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name can have at most 100 characters")
    private String name;

    private Long parentId;
}
