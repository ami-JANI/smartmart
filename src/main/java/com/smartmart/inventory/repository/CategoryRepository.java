package com.smartmart.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartmart.inventory.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAsc();

    /** A null {@code parentId} looks among the top-level categories. */
    boolean existsByParent_IdAndName(Long parentId, String name);

    boolean existsByParent_Id(Long parentId);
}
