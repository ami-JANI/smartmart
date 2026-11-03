package com.smartmart.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.smartmart.inventory.domain.Category;
import com.smartmart.inventory.domain.Product;
import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.domain.StockBatch;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;

class StockBatchServiceImplTest {

    private final StockBatchRepository batches = mock(StockBatchRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final StockBatchServiceImpl service = new StockBatchServiceImpl(batches, products);

    private Product product(SaleUnit unit) {
        Product product = new Product("Rice", "1234", new Category("Food", null), unit, BigDecimal.TEN, null, true);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        return product;
    }

    private StockBatchData data(String quantity) {
        return new StockBatchData(" B-1 ", new BigDecimal(quantity), new BigDecimal("40.00"), LocalDate.of(2030, 1, 1));
    }

    @Test
    void addSavesTheDelivery() {
        Product rice = product(SaleUnit.PIECE);

        service.add(1L, data("12"));

        ArgumentCaptor<StockBatch> saved = ArgumentCaptor.forClass(StockBatch.class);
        verify(batches).save(saved.capture());
        assertThat(saved.getValue().getProduct()).isSameAs(rice);
        assertThat(saved.getValue().getBatchNumber()).isEqualTo("B-1");
        assertThat(saved.getValue().getQuantity()).isEqualByComparingTo("12");
    }

    @Test
    void pieceProductRefusesADecimalQuantity() {
        product(SaleUnit.PIECE);

        assertThatThrownBy(() -> service.add(1L, data("2.5")))
                .isInstanceOf(ProductRuleException.class)
                .extracting("field").isEqualTo("quantity");
        verify(batches, never()).save(any());
    }

    @Test
    void weighedProductAcceptsThreeDecimalsAndRefusesFour() {
        product(SaleUnit.KG);

        service.add(1L, data("1.125"));

        assertThatThrownBy(() -> service.add(1L, data("1.1255")))
                .isInstanceOf(ProductRuleException.class);
    }

    @Test
    void missingProductIsNotFound() {
        when(products.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(1L, data("1")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void forProductMarksOnlyBatchesPastTheirExpiryDate() {
        Product rice = product(SaleUnit.PIECE);
        LocalDate today = LocalDate.now();
        when(batches.findForProduct(1L)).thenReturn(List.of(
                new StockBatch(rice, "old", BigDecimal.ONE, BigDecimal.ONE, today.minusDays(1)),
                new StockBatch(rice, "today", BigDecimal.ONE, BigDecimal.ONE, today),
                new StockBatch(rice, "none", BigDecimal.ONE, BigDecimal.ONE, null)));

        assertThat(service.forProduct(1L)).extracting(StockBatchSummary::expired)
                .containsExactly(true, false, false);
    }
}
