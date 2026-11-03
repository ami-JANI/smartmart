package com.smartmart.pos.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The items a cashier is selling, one line per product, in the order they were
 * first scanned. The originator in the Memento pattern: it creates snapshots of
 * itself and restores itself from them, for undo (FR-POS-4).
 */
public class Cart implements Serializable {

    private final Map<Long, CartLine> lines = new LinkedHashMap<>();

    /** Adds a line, or increases the quantity if the product is already in the cart. */
    public void add(CartLine line) {
        CartLine existing = lines.get(line.productId());
        lines.put(line.productId(), existing == null
                ? line
                : existing.withQuantity(existing.quantity().add(line.quantity())));
    }

    public void changeQuantity(Long productId, BigDecimal quantity) {
        lines.put(productId, lineFor(productId).withQuantity(quantity));
    }

    public void remove(Long productId) {
        lineFor(productId);
        lines.remove(productId);
    }

    public void clear() {
        lines.clear();
    }

    public CartSnapshot snapshot() {
        return new CartSnapshot(lines());
    }

    public void restore(CartSnapshot snapshot) {
        lines.clear();
        snapshot.lines().forEach(line -> lines.put(line.productId(), line));
    }

    public List<CartLine> lines() {
        return List.copyOf(lines.values());
    }

    public BigDecimal subtotal() {
        return lines.values().stream().map(CartLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CartLine lineFor(Long productId) {
        CartLine line = lines.get(productId);
        if (line == null) {
            throw new CartRuleException("That item is no longer in the cart");
        }
        return line;
    }
}
