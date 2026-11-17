package com.smartmart.pos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.smartmart.common.config.StoreSettings;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.pos.domain.Cart;
import com.smartmart.pos.domain.CartHistory;
import com.smartmart.pos.domain.CartRuleException;
import com.smartmart.pos.domain.DiscountStrategy;
import com.smartmart.pos.domain.FlatAmountDiscount;
import com.smartmart.pos.domain.PercentageDiscount;

class CartServiceImplTest {

    private final ProductService products = mock(ProductService.class);
    private final DiscountService discounts = mock(DiscountService.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final CartServiceImpl service = new CartServiceImpl(new Cart(), new CartHistory(), products,
            new StoreSettings(new BigDecimal("10"), new BigDecimal("15"), 7), discounts, customers);

    private void discount(long id, String name, DiscountStrategy strategy) {
        when(discounts.findActive(id)).thenReturn(Optional.of(new ActiveDiscount(id, name, strategy)));
    }

    private void product(long id, String barcode, String unit, String price) {
        when(products.findActiveByBarcode(barcode)).thenReturn(Optional.of(new ProductSummary(id, "Item " + id, barcode,
                1L, "Food", new BigDecimal(price), unit, null, true, BigDecimal.ZERO, true)));
    }

    @Test
    void unknownOrInactiveBarcodeIsRefusedAndAddsNothing() {
        when(products.findActiveByBarcode("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.scan("9999", null))
                .isInstanceOf(CartRuleException.class).hasMessage("Product not found");
        assertThat(service.view().lines()).isEmpty();
    }

    @Test
    void pieceProductAddsOneUnitPerScan() {
        product(1, "1111", "PIECE", "10.00");

        assertThat(service.scan(" 1111 ", null)).isEmpty();
        service.scan("1111", null);

        CartLineView line = service.view().lines().get(0);
        assertThat(line.quantity()).isEqualByComparingTo("2");
        assertThat(line.lineTotal()).isEqualByComparingTo("20.00");
        assertThat(line.saleUnit()).isEqualTo("PIECE");
        assertThat(line.free()).isFalse();
    }

    @Test
    void weighedProductAsksForTheWeightFirst() {
        product(2, "2222", "KG", "75.50");

        assertThat(service.scan("2222", null)).contains(new WeightPrompt("2222", "Item 2"));
        assertThat(service.view().lines()).isEmpty();

        assertThat(service.scan("2222", new BigDecimal("1.250"))).isEmpty();
        assertThat(service.view().lines().get(0).lineTotal()).isEqualByComparingTo("94.38");
    }

    @Test
    void weighedProductRefusesABadWeight() {
        product(2, "2222", "KG", "75.50");

        assertThatThrownBy(() -> service.scan("2222", BigDecimal.ZERO))
                .isInstanceOfSatisfying(WeightRejectedException.class,
                        refused -> assertThat(refused.prompt()).isEqualTo(new WeightPrompt("2222", "Item 2")));
        assertThatThrownBy(() -> service.scan("2222", new BigDecimal("1.2345"))).isInstanceOf(WeightRejectedException.class);
        assertThat(service.view().lines()).isEmpty();
    }

    @Test
    void totalsFollowTheFormula() {
        product(1, "1111", "PIECE", "115.00");
        service.scan("1111", null);

        CartView cart = service.view();

        assertThat(cart.subtotal()).isEqualByComparingTo("115.00");
        assertThat(cart.total()).isEqualByComparingTo("115.00");
        assertThat(cart.vatIncluded()).isEqualByComparingTo("15.00");
    }

    @Test
    void vatRoundsHalfUpToTwoDecimals() {
        product(1, "1111", "PIECE", "100.00");
        service.scan("1111", null);

        assertThat(service.view().vatIncluded()).isEqualByComparingTo("13.04");
    }

    @Test
    void emptyCartHasZeroTotals() {
        CartView cart = service.view();

        assertThat(cart.total()).isEqualByComparingTo("0");
        assertThat(cart.vatIncluded()).isEqualByComparingTo("0");
        assertThat(cart.canUndo()).isFalse();
    }

    @Test
    void undoReversesAddQuantityAndRemoveOneStepAtATime() {
        product(1, "1111", "PIECE", "10.00");
        product(2, "2222", "PIECE", "5.00");
        service.scan("1111", null);
        service.scan("2222", null);
        service.changeQuantity(1L, new BigDecimal("4"));
        service.remove(2L);
        assertThat(service.view().subtotal()).isEqualByComparingTo("40.00");

        service.undo();
        assertThat(service.view().subtotal()).isEqualByComparingTo("45.00");

        service.undo();
        assertThat(service.view().subtotal()).isEqualByComparingTo("15.00");

        service.undo();
        assertThat(service.view().subtotal()).isEqualByComparingTo("10.00");

        service.undo();
        assertThat(service.view().lines()).isEmpty();
        assertThat(service.view().canUndo()).isFalse();
    }

    @Test
    void undoWithNothingToUndoIsRefused() {
        assertThatThrownBy(service::undo)
                .isInstanceOf(CartRuleException.class).hasMessage("Nothing to undo");
    }

    @Test
    void refusedQuantityIsNotAnUndoStep() {
        product(1, "1111", "PIECE", "10.00");
        service.scan("1111", null);

        assertThatThrownBy(() -> service.changeQuantity(1L, BigDecimal.ZERO)).isInstanceOf(CartRuleException.class);

        service.undo();
        assertThat(service.view().lines()).isEmpty();
    }

    @Test
    void weightPromptIsNotAnUndoStep() {
        product(2, "2222", "KG", "75.50");

        service.scan("2222", null);

        assertThat(service.view().canUndo()).isFalse();
    }

    @Test
    void settingTheSameQuantityIsNotAnUndoStep() {
        product(1, "1111", "PIECE", "10.00");
        service.scan("1111", null);

        service.changeQuantity(1L, BigDecimal.ONE);
        service.undo();

        assertThat(service.view().lines()).isEmpty();
    }

    @Test
    void theLastTwentyChangesCanBeUndoneAndOlderOnesAreForgotten() {
        product(1, "1111", "PIECE", "10.00");
        for (int i = 0; i < 21; i++) {
            service.scan("1111", null);
        }

        for (int i = 0; i < 20; i++) {
            service.undo();
        }

        assertThat(service.view().lines().get(0).quantity()).isEqualByComparingTo("1");
        assertThat(service.view().canUndo()).isFalse();
    }

    @Test
    void clearEmptiesTheCartAndItsHistory() {
        product(1, "1111", "PIECE", "10.00");
        service.scan("1111", null);

        service.clear();

        assertThat(service.view().lines()).isEmpty();
        assertThat(service.view().canUndo()).isFalse();
    }

    @Test
    void changeAndRemoveUseTheLineId() {
        product(1, "1111", "PIECE", "10.00");
        service.scan("1111", null);
        Long lineId = service.view().lines().get(0).id();

        service.changeQuantity(lineId, new BigDecimal("4"));
        assertThat(service.view().subtotal()).isEqualByComparingTo("40.00");

        service.remove(lineId);
        assertThat(service.view().lines()).isEmpty();
    }

    @Test
    void discountComesOffTheSubtotalAndVatIsOnTheNewTotal() {
        product(1, "1111", "PIECE", "500.00");
        service.scan("1111", null);
        discount(7, "Staff", new PercentageDiscount(new BigDecimal("10")));

        service.applyDiscount(7L);

        CartView cart = service.view();
        assertThat(cart.discountId()).isEqualTo(7L);
        assertThat(cart.discountName()).isEqualTo("Staff");
        assertThat(cart.discountAmount()).isEqualByComparingTo("50.00");
        assertThat(cart.total()).isEqualByComparingTo("450.00");
        assertThat(cart.vatIncluded()).isEqualByComparingTo("58.70");
    }

    @Test
    void flatDiscountIsCappedAtTheSubtotal() {
        product(1, "1111", "PIECE", "60.00");
        service.scan("1111", null);
        discount(8, "Flat 100", new FlatAmountDiscount(new BigDecimal("100")));

        service.applyDiscount(8L);

        assertThat(service.view().discountAmount()).isEqualByComparingTo("60.00");
        assertThat(service.view().total()).isEqualByComparingTo("0.00");
    }

    @Test
    void anotherDiscountReplacesTheFirstAndNullRemovesIt() {
        product(1, "1111", "PIECE", "200.00");
        service.scan("1111", null);
        discount(7, "Staff", new PercentageDiscount(new BigDecimal("10")));
        discount(8, "Flat 50", new FlatAmountDiscount(new BigDecimal("50")));

        service.applyDiscount(7L);
        service.applyDiscount(8L);
        assertThat(service.view().discountAmount()).isEqualByComparingTo("50.00");

        service.applyDiscount(null);
        assertThat(service.view().discountId()).isNull();
        assertThat(service.view().total()).isEqualByComparingTo("200.00");
    }

    @Test
    void unknownOrEndedDiscountIsRefusedAndChangesNothing() {
        when(discounts.findActive(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyDiscount(9L))
                .isInstanceOf(CartRuleException.class).hasMessage("That discount is not available");
        assertThat(service.view().canUndo()).isFalse();
    }

    @Test
    void discountThatEndedAfterItWasPickedNoLongerCounts() {
        product(1, "1111", "PIECE", "100.00");
        service.scan("1111", null);
        discount(7, "Staff", new PercentageDiscount(new BigDecimal("10")));
        service.applyDiscount(7L);

        when(discounts.findActive(7L)).thenReturn(Optional.empty());

        assertThat(service.view().discountAmount()).isEqualByComparingTo("0");
        assertThat(service.view().total()).isEqualByComparingTo("100.00");
    }

    @Test
    void undoReversesAnAppliedDiscount() {
        product(1, "1111", "PIECE", "100.00");
        service.scan("1111", null);
        discount(7, "Staff", new PercentageDiscount(new BigDecimal("10")));
        service.applyDiscount(7L);

        service.undo();

        assertThat(service.view().discountId()).isNull();
        assertThat(service.view().lines()).hasSize(1);
    }

    @Test
    void clearRemovesTheDiscount() {
        discount(7, "Staff", new PercentageDiscount(new BigDecimal("10")));
        service.applyDiscount(7L);

        service.clear();

        assertThat(service.view().discountId()).isNull();
    }

    @Test
    void memberIsFoundByPhoneAndClearedWithTheCart() {
        CustomerSummary farhana = new CustomerSummary(5L, "Farhana Akter", "01711000101", java.time.Instant.EPOCH);
        when(customers.findByPhone("01711-000101")).thenReturn(Optional.of(farhana));
        when(customers.find(5L)).thenReturn(Optional.of(farhana));

        service.addMember("01711-000101");
        assertThat(service.member()).contains(farhana);

        service.clear();
        assertThat(service.member()).isEmpty();
    }

    @Test
    void unknownMemberPhoneIsRefused() {
        when(customers.findByPhone("0100")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember("0100"))
                .isInstanceOf(CartRuleException.class)
                .hasMessage("No member with that phone number");
    }

    @Test
    void addingAMemberIsNotAnUndoStep() {
        CustomerSummary member = new CustomerSummary(5L, "Farhana Akter", "01711000101", java.time.Instant.EPOCH);
        when(customers.findByPhone("01711000101")).thenReturn(Optional.of(member));

        service.addMember("01711000101");

        assertThat(service.view().canUndo()).isFalse();
    }
}
