package com.smartmart.inventory.service;

/** A category for a select list. {@code path} shows its place in the tree, for example "Food > Rice". */
public record CategoryOption(Long id, String path) {
}
