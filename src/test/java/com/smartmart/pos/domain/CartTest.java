package com.smartmart.pos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.smartmart.inventory.domain.SaleUnit;

class CartTest {

    private final Cart cart = new Cart();

    private CartLine piece(long id, String price, String quantity) {
        return new CartLine(id, "Item " + id, "000" + id, SaleUnit.PIECE, new BigDecimal(price), new BigDecimal(quantity));
    }

    private CartLine weighed(long id, String price, String quantity) {
        return new CartLine(id, "Item " + id, "000" + id, SaleUnit.KG, new BigDecimal(price), new BigDecimal(quantity));
    }

    @Test
    void scanningAgainIncreasesTheSameLine() {
        cart.add(piece(1, "10.00", "1"));
        cart.add(piece(1, "10.00", "1"));
        cart.add(piece(2, "5.00", "1"));

        assertThat(cart.lines()).hasSize(2);
        assertThat(cart.lines().get(0).quantity()).isEqualByComparingTo("2");
        assertThat(cart.subtotal()).isEqualByComparingTo("25.00");
    }

    @Test
    void lineTotalRoundsHalfUpToTwoDecimals() {
        assertThat(weighed(1, "75.50", "1.255").total()).isEqualByComparingTo("94.75");
        assertThat(weighed(1, "10.05", "0.5").total()).isEqualByComparingTo("5.03");
    }

    @Test
    void emptyCartHasZeroSubtotal() {
        assertThat(cart.lines()).isEmpty();
        assertThat(cart.subtotal()).isEqualByComparingTo("0");
    }

    @Test
    void pieceQuantityBoundaries() {
        assertThat(piece(1, "1", "1").quantity()).isEqualByComparingTo("1");
        assertThat(piece(1, "1", "9999").quantity()).isEqualByComparingTo("9999");

        assertThatThrownBy(() -> piece(1, "1", "0"))
                .isInstanceOf(CartRuleException.class).hasMessageContaining("whole number");
        assertThatThrownBy(() -> piece(1, "1", "-1")).isInstanceOf(CartRuleException.class);
        assertThatThrownBy(() -> piece(1, "1", "1.5")).isInstanceOf(CartRuleException.class);
        assertThatThrownBy(() -> piece(1, "1", "10000"))
                .isInstanceOf(CartRuleException.class).hasMessageContaining("at most 9999");
    }

    @Test
    void weightBoundaries() {
        assertThat(weighed(1, "1", "0.001").quantity()).isEqualByComparingTo("0.001");

        assertThatThrownBy(() -> weighed(1, "1", "0"))
                .isInstanceOf(CartRuleException.class).hasMessageContaining("Weight");
        assertThatThrownBy(() -> weighed(1, "1", "-0.5")).isInstanceOf(CartRuleException.class);
        assertThatThrownBy(() -> weighed(1, "1", "0.0001")).isInstanceOf(CartRuleException.class);
    }

    @Test
    void refusedChangeLeavesTheCartAsItWas() {
        cart.add(piece(1, "10.00", "9999"));

        assertThatThrownBy(() -> cart.add(piece(1, "10.00", "1"))).isInstanceOf(CartRuleException.class);
        assertThatThrownBy(() -> cart.changeQuantity(1L, BigDecimal.ZERO)).isInstanceOf(CartRuleException.class);

        assertThat(cart.lines().get(0).quantity()).isEqualByComparingTo("9999");
    }

    @Test
    void changeAndRemoveWork() {
        cart.add(piece(1, "10.00", "1"));
        cart.add(piece(2, "5.00", "1"));

        cart.changeQuantity(1L, new BigDecimal("3"));
        cart.remove(2L);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.subtotal()).isEqualByComparingTo("30.00");
    }

    @Test
    void lineNotInTheCartIsRefused() {
        assertThatThrownBy(() -> cart.remove(9L)).isInstanceOf(CartRuleException.class);
        assertThatThrownBy(() -> cart.changeQuantity(9L, BigDecimal.ONE)).isInstanceOf(CartRuleException.class);
    }
}
