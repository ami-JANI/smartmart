package com.smartmart.pos.domain;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * The caretaker in the Memento pattern: keeps the cart snapshots without looking
 * inside them. Holds the last 20 changes and forgets older ones (FR-POS-4).
 */
public class CartHistory implements Serializable {

    static final int MAX_CHANGES = 20;

    private final Deque<CartSnapshot> snapshots = new ArrayDeque<>();

    public void push(CartSnapshot snapshot) {
        snapshots.push(snapshot);
        if (snapshots.size() > MAX_CHANGES) {
            snapshots.removeLast();
        }
    }

    public Optional<CartSnapshot> pop() {
        return Optional.ofNullable(snapshots.poll());
    }

    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    public void clear() {
        snapshots.clear();
    }
}
