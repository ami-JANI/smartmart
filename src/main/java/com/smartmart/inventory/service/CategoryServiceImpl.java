package com.smartmart.inventory.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.inventory.domain.Category;
import com.smartmart.inventory.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CategoryServiceImpl implements CategoryService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final String CANNOT_DELETE = "Cannot delete: this category has products or child categories";

    private final CategoryRepository categories;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category category : categories.findAllByOrderByNameAsc()) {
            byParent.computeIfAbsent(parentIdOf(category), key -> new ArrayList<>()).add(category);
        }
        return nodesUnder(null, byParent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryOption> options() {
        List<CategoryOption> options = new ArrayList<>();
        addOptions(tree(), "", options);
        return options;
    }

    @Override
    @Transactional
    public Long create(String name, Long parentId) {
        String cleanName = validName(name);
        Category parent = parentId == null ? null : find(parentId);
        requireNameFree(parentId, cleanName);
        return saveChecked(new Category(cleanName, parent)).getId();
    }

    @Override
    @Transactional
    public void rename(Long id, String name) {
        Category category = find(id);
        String cleanName = validName(name);
        if (cleanName.equals(category.getName())) {
            return;
        }
        requireNameFree(parentIdOf(category), cleanName);
        category.rename(cleanName);
        saveChecked(category);
    }

    @Override
    @Transactional
    public void move(Long id, Long parentId) {
        Category category = find(id);
        if (Objects.equals(parentIdOf(category), parentId)) {
            return;
        }
        Category parent = parentId == null ? null : find(parentId);
        requireNotInOwnBranch(category, parent);
        requireNameFree(parentId, category.getName());
        category.moveUnder(parent);
        saveChecked(category);
    }

    // SCRUM-24 adds the check for products in this category.
    @Override
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (categories.existsByParent_Id(id)) {
            throw new CategoryRuleException(CANNOT_DELETE);
        }
        try {
            categories.delete(category);
            categories.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CategoryRuleException(CANNOT_DELETE);
        }
    }

    private List<CategoryNode> nodesUnder(Long parentId, Map<Long, List<Category>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(category -> new CategoryNode(category.getId(), category.getName(), parentId,
                        nodesUnder(category.getId(), byParent)))
                .toList();
    }

    private void addOptions(List<CategoryNode> nodes, String prefix, List<CategoryOption> options) {
        for (CategoryNode node : nodes) {
            String path = prefix + node.name();
            options.add(new CategoryOption(node.id(), path));
            addOptions(node.children(), path + " > ", options);
        }
    }

    private Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> new CategoryRuleException("Category not found"));
    }

    private Long parentIdOf(Category category) {
        return category.getParent() == null ? null : category.getParent().getId();
    }

    private String validName(String name) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            throw new CategoryRuleException("Category name is required");
        }
        if (cleanName.length() > MAX_NAME_LENGTH) {
            throw new CategoryRuleException("Category name can have at most " + MAX_NAME_LENGTH + " characters");
        }
        return cleanName;
    }

    private void requireNameFree(Long parentId, String name) {
        if (categories.existsByParent_IdAndName(parentId, name)) {
            throw new CategoryRuleException(duplicateMessage(name));
        }
    }

    private void requireNotInOwnBranch(Category category, Category newParent) {
        for (Category current = newParent; current != null; current = current.getParent()) {
            if (current.getId().equals(category.getId())) {
                throw new CategoryRuleException("A category cannot be moved under itself or one of its own child categories");
            }
        }
    }

    // The database constraint catches two people saving the same name at once.
    private Category saveChecked(Category category) {
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new CategoryRuleException(duplicateMessage(category.getName()));
        }
    }

    private String duplicateMessage(String name) {
        return "A category named \"" + name + "\" already exists here";
    }
}
