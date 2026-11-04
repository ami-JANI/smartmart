package com.smartmart.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.domain.StockBatch;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;

/**
 * Deducts stock against the two databases from compose.yaml, to check the
 * batch query: expired and empty batches are skipped, the soonest expiry is
 * used first, and batches without an expiry date come last.
 */
@SpringBootTest
class StockDeductionTests {

    private static final LocalDate TODAY = LocalDate.now();

    private final StockBatchService stock;
    private final ProductService products;
    private final CategoryService categories;
    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private Long categoryId;
    private Long productId;

    @Autowired
    StockDeductionTests(StockBatchService stock, ProductService products, CategoryService categories,
                        ProductRepository productRepository, StockBatchRepository batchRepository) {
        this.stock = stock;
        this.products = products;
        this.categories = categories;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    @BeforeEach
    void createProduct() {
        categoryId = categories.create("Test-" + UUID.randomUUID(), null);
        String barcode = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
        productId = products.create(new ProductData("Test milk", barcode, categoryId, SaleUnit.PIECE,
                new BigDecimal("90.00"), null, true));
    }

    @AfterEach
    void removeTestData() {
        batchRepository.deleteAll(batchRepository.findForProduct(productId));
        productRepository.deleteById(productId);
        categories.delete(categoryId);
    }

    private void batch(String number, int quantity, LocalDate expiry) {
        stock.add(productId, new StockBatchData(number, new BigDecimal(quantity), new BigDecimal("70.00"), expiry));
    }

    private Map<String, BigDecimal> quantities() {
        return batchRepository.findForProduct(productId).stream()
                .collect(Collectors.toMap(StockBatch::getBatchNumber, StockBatch::getQuantity));
    }

    @Test
    void takesFromTheSoonestUnexpiredBatchesAndLeavesNoExpiryLast() {
        batch("EXPIRED", 10, TODAY.minusDays(1));
        batch("NO-EXPIRY", 10, null);
        batch("LATER", 3, TODAY.plusDays(10));
        batch("TODAY", 2, TODAY);

        stock.deduct(productId, new BigDecimal("6"));

        assertThat(quantities()).containsEntry("EXPIRED", new BigDecimal("10.000"))
                .containsEntry("TODAY", new BigDecimal("0.000"))
                .containsEntry("LATER", new BigDecimal("0.000"))
                .containsEntry("NO-EXPIRY", new BigDecimal("9.000"));
    }

    @Test
    void expiredStockDoesNotCount() {
        batch("EXPIRED", 10, TODAY.minusDays(1));
        batch("FRESH", 2, TODAY.plusDays(3));

        assertThatThrownBy(() -> stock.deduct(productId, new BigDecimal("3")))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for Test milk");
        assertThat(quantities()).containsEntry("FRESH", new BigDecimal("2.000"));
    }
}
