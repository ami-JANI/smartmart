package com.smartmart.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.smartmart.inventory.domain.Category;
import com.smartmart.inventory.domain.Product;
import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.domain.StockBatch;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;

/**
 * Basis path tests for {@link StockBatchServiceImpl#deduct} (GUIDELINES.md
 * section 9). The method has four decisions: product found, enough stock,
 * more batches in the loop, and nothing left to take. V(G) = 4 + 1 = 5, so
 * five independent paths, one test each (see docs/testing/basis-paths.md).
 */
class StockDeductionTest {

    private final StockBatchRepository batches = mock(StockBatchRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final StockBatchServiceImpl service = new StockBatchServiceImpl(batches, products);
    private final Product rice = new Product("Miniket rice", "1234", new Category("Food", null), SaleUnit.KG,
            BigDecimal.TEN, null, true);

    private StockBatch batch(String quantity) {
        return new StockBatch(rice, "B", new BigDecimal(quantity), BigDecimal.ONE, LocalDate.of(2030, 1, 1));
    }

    private void sellable(StockBatch... inExpiryOrder) {
        when(products.findById(1L)).thenReturn(Optional.of(rice));
        when(batches.lockSellable(eq(1L), any())).thenReturn(List.of(inExpiryOrder));
    }

    @Test
    void path1MissingProductIsNotFound() {
        when(products.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deduct(1L, BigDecimal.ONE))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void path2NotEnoughStockIsRefusedAndNothingIsTaken() {
        StockBatch first = batch("2");
        StockBatch second = batch("1.5");
        sellable(first, second);

        assertThatThrownBy(() -> service.deduct(1L, new BigDecimal("3.501")))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for Miniket rice");
        assertThat(first.getQuantity()).isEqualByComparingTo("2");
        assertThat(second.getQuantity()).isEqualByComparingTo("1.5");
    }

    @Test
    void path3NoBatchesAndNothingWantedTakesNothing() {
        sellable();

        service.deduct(1L, BigDecimal.ZERO);
    }

    @Test
    void path4OneBatchWithExactlyEnoughIsEmptied() {
        StockBatch only = batch("3");
        sellable(only);

        service.deduct(1L, new BigDecimal("3"));

        assertThat(only.getQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void path5SoonestBatchCoversItAndLaterBatchesAreUntouched() {
        StockBatch soonest = batch("5");
        StockBatch later = batch("4");
        sellable(soonest, later);

        service.deduct(1L, new BigDecimal("2"));

        assertThat(soonest.getQuantity()).isEqualByComparingTo("3");
        assertThat(later.getQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void shortfallInTheSoonestBatchComesFromTheNext() {
        StockBatch soonest = batch("2");
        StockBatch later = batch("4");
        sellable(soonest, later);

        service.deduct(1L, new BigDecimal("3.25"));

        assertThat(soonest.getQuantity()).isEqualByComparingTo("0");
        assertThat(later.getQuantity()).isEqualByComparingTo("2.75");
    }
}
