package com.smartmart.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.common.config.StoreSettings;
import com.smartmart.inventory.domain.Category;
import com.smartmart.inventory.domain.Product;
import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.repository.CategoryRepository;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;

class ProductServiceImplTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final StockBatchRepository batches = mock(StockBatchRepository.class);
    private final ProductServiceImpl service =
            new ProductServiceImpl(products, categories, batches, new StoreSettings(new BigDecimal("10"), new BigDecimal("15")));
    private final Category rice = new Category("Rice", null);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rice, "id", 1L);
        when(categories.findById(1L)).thenReturn(Optional.of(rice));
    }

    private ProductData data(String barcode, SaleUnit unit, BigDecimal threshold) {
        return new ProductData(" Miniket ", barcode, 1L, unit, new BigDecimal("75.50"), threshold, true);
    }

    private Product stored(long id, BigDecimal threshold) {
        Product product = new Product("Miniket", "1234", rice, SaleUnit.PIECE, new BigDecimal("75.50"), threshold, true);
        ReflectionTestUtils.setField(product, "id", id);
        when(products.findById(id)).thenReturn(Optional.of(product));
        return product;
    }

    @Test
    void createSavesAndReturnsTheId() {
        Product saved = stored(5L, null);
        when(products.saveAndFlush(any())).thenReturn(saved);

        assertThat(service.create(data("1234", SaleUnit.PIECE, null))).isEqualTo(5L);
    }

    @Test
    void createRefusesATakenBarcode() {
        when(products.existsByBarcode("1234")).thenReturn(true);

        assertThatThrownBy(() -> service.create(data("1234", SaleUnit.PIECE, null)))
                .isInstanceOf(ProductRuleException.class)
                .hasMessage("Barcode already in use")
                .extracting("field").isEqualTo("barcode");
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void barcodeTakenAtTheSameMomentIsRefusedToo() {
        when(products.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.create(data("1234", SaleUnit.PIECE, null)))
                .isInstanceOf(ProductRuleException.class)
                .hasMessage("Barcode already in use");
    }

    @Test
    void createRefusesAMissingCategory() {
        when(categories.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(data("1234", SaleUnit.PIECE, null)))
                .isInstanceOf(ProductRuleException.class)
                .extracting("field").isEqualTo("categoryId");
    }

    @Test
    void pieceProductRefusesADecimalThreshold() {
        assertThatThrownBy(() -> service.create(data("1234", SaleUnit.PIECE, new BigDecimal("2.5"))))
                .isInstanceOf(ProductRuleException.class)
                .extracting("field").isEqualTo("lowStockThreshold");
    }

    @Test
    void pieceProductAcceptsAWholeNumberWrittenWithDecimals() {
        Product saved = stored(5L, null);
        when(products.saveAndFlush(any())).thenReturn(saved);

        service.create(data("1234", SaleUnit.PIECE, new BigDecimal("2.000")));
    }

    @Test
    void weighedProductAcceptsADecimalThreshold() {
        Product saved = stored(5L, null);
        when(products.saveAndFlush(any())).thenReturn(saved);

        service.create(data("1234", SaleUnit.KG, new BigDecimal("2.125")));
    }

    @Test
    void updateChangesTheFieldsAndKeepsItsOwnBarcode() {
        Product product = stored(5L, null);

        service.update(5L, data("1234", SaleUnit.KG, null));

        assertThat(product.getSaleUnit()).isEqualTo(SaleUnit.KG);
        assertThat(product.getName()).isEqualTo("Miniket");
        verify(products).existsByBarcodeAndIdNot("1234", 5L);
    }

    @Test
    void updateRefusesABarcodeOfAnotherProduct() {
        stored(5L, null);
        when(products.existsByBarcodeAndIdNot("9999", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, data("9999", SaleUnit.PIECE, null)))
                .isInstanceOf(ProductRuleException.class)
                .hasMessage("Barcode already in use");
    }

    @Test
    void missingProductIsNotFound() {
        when(products.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void lowStockUsesTheProductThresholdThenTheStoreDefault() {
        Product product = new Product("Miniket", "1234", rice, SaleUnit.PIECE, BigDecimal.ONE, null, true);
        assertThat(product.isLowOnStock(new BigDecimal("10"), new BigDecimal("10"))).isTrue();
        assertThat(product.isLowOnStock(new BigDecimal("11"), new BigDecimal("10"))).isFalse();

        Product ownThreshold = new Product("Miniket", "1234", rice, SaleUnit.PIECE, BigDecimal.ONE, new BigDecimal("3"), true);
        assertThat(ownThreshold.isLowOnStock(new BigDecimal("3"), new BigDecimal("10"))).isTrue();
        assertThat(ownThreshold.isLowOnStock(new BigDecimal("4"), new BigDecimal("10"))).isFalse();
    }

    @Test
    void saleUnitsAllowTheRightDecimals() {
        assertThat(SaleUnit.PIECE.allows(new BigDecimal("3"))).isTrue();
        assertThat(SaleUnit.PIECE.allows(new BigDecimal("3.5"))).isFalse();
        assertThat(SaleUnit.KG.allows(new BigDecimal("1.125"))).isTrue();
        assertThat(SaleUnit.KG.allows(new BigDecimal("1.1255"))).isFalse();
    }
}
