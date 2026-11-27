package com.smartmart.pos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import com.smartmart.inventory.service.InsufficientStockException;
import com.smartmart.inventory.service.StockBatchService;
import com.smartmart.pos.domain.CartRuleException;
import com.smartmart.pos.domain.Sale;
import com.smartmart.pos.domain.SaleLine;
import com.smartmart.pos.repository.SaleRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Basis path tests for checkout (docs/testing/basis-paths.md section 3).
 * The transaction is replaced by a direct call; the database tests in
 * CheckoutPageTests check that a refused sale saves nothing.
 */
class CheckoutFacadeImplTest {

    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");

    private final CartService carts = mock(CartService.class);
    private final StockBatchService stock = mock(StockBatchService.class);
    private final SaleRepository sales = mock(SaleRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final CheckoutFacadeImpl checkout = new CheckoutFacadeImpl(carts, new CashPayment(), stock, sales,
            TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC), meters);

    private static CartLineView line(long productId, String price, String quantity, String total) {
        return new CartLineView(productId, "Item " + productId, "100" + productId, "PIECE",
                new BigDecimal(price), new BigDecimal(quantity), new BigDecimal(total), false);
    }

    private void cart(List<CartLineView> lines, Long discountId, String subtotal, String discount, String total) {
        when(carts.view()).thenReturn(new CartView(lines, new BigDecimal(subtotal), BigDecimal.ZERO, discountId,
                discountId == null ? null : "Staff", new BigDecimal(discount), new BigDecimal(total),
                BigDecimal.ZERO, false));
    }

    private Sale savedSale() {
        ArgumentCaptor<Sale> saved = ArgumentCaptor.forClass(Sale.class);
        verify(sales).save(saved.capture());
        return saved.getValue();
    }

    private void saveGivesId(long id) {
        when(sales.save(any(Sale.class))).thenAnswer(call -> {
            Sale sale = call.getArgument(0);
            ReflectionTestUtils.setField(sale, "id", id);
            return sale;
        });
    }

    // Path 1: the cart is empty.
    @Test
    void emptyCartIsRefusedAndNothingHappens() {
        cart(List.of(), null, "0.00", "0.00", "0.00");

        assertThatThrownBy(() -> checkout.completeSale(5L, new PaymentDetails(BigDecimal.TEN)))
                .isInstanceOf(CartRuleException.class).hasMessage("The cart is empty");
        verifyNoInteractions(stock, sales);
        verify(carts, never()).clear();
    }

    // Path 2: one line, so the loop runs once.
    @Test
    void oneLineSaleIsSavedWithItsPaymentAndTheCartIsCleared() {
        cart(List.of(line(1, "500.00", "1", "500.00")), 7L, "500.00", "50.00", "450.00");
        saveGivesId(42);

        Long saleId = checkout.completeSale(5L, new PaymentDetails(new BigDecimal("500")));

        assertThat(saleId).isEqualTo(42L);
        verify(stock).deduct(1L, BigDecimal.ONE);
        Sale sale = savedSale();
        assertThat(sale.getCashierId()).isEqualTo(5L);
        assertThat(sale.getDiscountId()).isEqualTo(7L);
        assertThat(sale.getSoldAt()).isEqualTo(NOW);
        assertThat(sale.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(sale.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(sale.getTotal()).isEqualByComparingTo("450.00");
        assertThat(sale.getPayment().getMethod()).isEqualTo("CASH");
        assertThat(sale.getPayment().getCashReceived()).isEqualByComparingTo("500.00");
        assertThat(sale.getPayment().getChangeDue()).isEqualByComparingTo("50.00");
        assertThat(sale.invoiceNumber()).isEqualTo("INV-000042");
        verify(carts).clear();
    }

    // Path 3: two lines, so the loop runs again; a weighed quantity is deducted as it is.
    @Test
    void everyLineIsDeductedAndSavedAtItsPrice() {
        cart(List.of(line(1, "10.00", "3", "30.00"), line(2, "75.50", "1.250", "94.38")),
                null, "124.38", "0.00", "124.38");
        saveGivesId(43);

        checkout.completeSale(5L, new PaymentDetails(new BigDecimal("124.38")));

        verify(stock).deduct(1L, new BigDecimal("3"));
        verify(stock).deduct(2L, new BigDecimal("1.250"));
        List<SaleLine> lines = savedSale().getLines();
        assertThat(lines).extracting(SaleLine::getProductId).containsExactly(1L, 2L);
        assertThat(lines.get(1).getUnitPrice()).isEqualByComparingTo("75.50");
        assertThat(lines.get(1).getLineTotal()).isEqualByComparingTo("94.38");
    }

    @Test
    void cashBelowTheTotalIsRefusedBeforeStockIsTouched() {
        cart(List.of(line(1, "10.00", "1", "10.00")), null, "10.00", "0.00", "10.00");

        assertThatThrownBy(() -> checkout.completeSale(5L, new PaymentDetails(new BigDecimal("9.99"))))
                .isInstanceOf(PaymentRefusedException.class);
        verifyNoInteractions(stock, sales);
        verify(carts, never()).clear();
    }

    @Test
    void notEnoughStockRefusesTheSaleAndKeepsTheCart() {
        cart(List.of(line(1, "10.00", "1", "10.00"), line(2, "5.00", "9", "45.00")),
                null, "55.00", "0.00", "55.00");
        doThrow(new InsufficientStockException("Item 2")).when(stock).deduct(2L, new BigDecimal("9"));

        assertThatThrownBy(() -> checkout.completeSale(5L, new PaymentDetails(new BigDecimal("100"))))
                .isInstanceOf(InsufficientStockException.class).hasMessage("Not enough stock for Item 2");
        verify(sales, never()).save(any());
        verify(carts, never()).clear();
    }

    @Test
    void aCompletedSaleIsCountedWithItsValueAndTiming() {
        cart(List.of(line(1, "500.00", "1", "500.00")), 7L, "500.00", "50.00", "450.00");
        saveGivesId(44);

        checkout.completeSale(5L, new PaymentDetails(new BigDecimal("500")));

        assertThat(meters.get("smartmart.sales").counter().count()).isEqualTo(1);
        assertThat(meters.get("smartmart.sales.revenue").counter().count()).isEqualTo(450.0);
        assertThat(meters.get("smartmart.checkout.duration").tag("outcome", "completed").timer().count()).isEqualTo(1);
    }

    @Test
    void aRefusedSaleIsTimedButNotCounted() {
        cart(List.of(), null, "0.00", "0.00", "0.00");

        assertThatThrownBy(() -> checkout.completeSale(5L, new PaymentDetails(BigDecimal.TEN)))
                .isInstanceOf(CartRuleException.class);

        assertThat(meters.get("smartmart.sales").counter().count()).isZero();
        assertThat(meters.get("smartmart.checkout.duration").tag("outcome", "refused").timer().count()).isEqualTo(1);
    }
}
