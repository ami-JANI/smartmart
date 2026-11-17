package com.smartmart.pos.domain;

import java.io.Serializable;
import java.util.List;

/** The memento in the Memento pattern: an immutable copy of a cart's state, for {@link Cart} to restore from. */
public record CartSnapshot(List<CartLine> lines, Long discountId) implements Serializable {

    public CartSnapshot {
        lines = List.copyOf(lines);
    }
}
