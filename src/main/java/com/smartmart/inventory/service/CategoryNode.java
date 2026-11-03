package com.smartmart.inventory.service;

import java.util.List;

/** One category in the tree, with its children sorted by name. {@code parentId} is null at the top level. */
public record CategoryNode(Long id, String name, Long parentId, List<CategoryNode> children) {
}
