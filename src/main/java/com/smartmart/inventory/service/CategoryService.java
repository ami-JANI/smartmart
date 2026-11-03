package com.smartmart.inventory.service;

import java.util.List;

/**
 * Manages the category tree (FR-INV-1). Change methods throw
 * {@link CategoryRuleException} when a rule refuses the change, or when a
 * given category does not exist.
 */
public interface CategoryService {

    /** Returns the top-level categories with their children. May lag slightly behind recent changes. */
    List<CategoryNode> tree();

    /** Returns every category in tree order. May lag slightly behind recent changes. */
    List<CategoryOption> options();

    /**
     * Adds a category.
     *
     * @param name     required, at most 100 characters, unique among its siblings
     * @param parentId the parent, or null for the top level
     * @return the new category's id
     */
    Long create(String name, Long parentId);

    /** Renames a category. The same name rules as {@link #create} apply. */
    void rename(Long id, String name);

    /** Moves a category, or to the top level when {@code parentId} is null. Refused under itself or a descendant. */
    void move(Long id, Long parentId);

    /** Deletes a category. Refused while it has child categories. */
    void delete(Long id);
}
