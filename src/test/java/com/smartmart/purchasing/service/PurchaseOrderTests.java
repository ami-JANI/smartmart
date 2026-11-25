package com.smartmart.purchasing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;
import com.smartmart.inventory.service.CategoryService;
import com.smartmart.inventory.service.ProductData;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.StockBatchService;
import com.smartmart.inventory.service.StockBatchSummary;
import com.smartmart.purchasing.domain.IllegalStateTransitionException;
import com.smartmart.purchasing.domain.PurchaseOrderStatus;
import com.smartmart.purchasing.repository.PurchaseOrderRepository;
import com.smartmart.purchasing.repository.SupplierRepository;

/**
 * Takes a purchase order from draft to closed against the databases from
 * compose.yaml: the schema, the row lock query, and the stock batches that
 * receiving adds (FR-PUR-4 to FR-PUR-7).
 */
@SpringBootTest
class PurchaseOrderTests {

    private static final LocalDate EXPIRY = LocalDate.now().plusMonths(6);

    private final PurchaseOrderService purchaseOrders;
    private final SupplierService suppliers;
    private final ProductService products;
    private final CategoryService categories;
    private final StockBatchService stock;
    private final PurchaseOrderRepository orderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private Long supplierId;
    private Long categoryId;
    private Long productId;
    private Long orderId;

    @Autowired
    PurchaseOrderTests(PurchaseOrderService purchaseOrders, SupplierService suppliers, ProductService products,
                       CategoryService categories, StockBatchService stock, PurchaseOrderRepository orderRepository,
                       SupplierRepository supplierRepository, ProductRepository productRepository,
                       StockBatchRepository batchRepository) {
        this.purchaseOrders = purchaseOrders;
        this.suppliers = suppliers;
        this.products = products;
        this.categories = categories;
        this.stock = stock;
        this.orderRepository = orderRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    @BeforeEach
    void createSupplierAndProduct() {
        supplierId = suppliers.create(new SupplierData("Test supplier " + UUID.randomUUID(), null, "01700000000",
                null, null, true));
        categoryId = categories.create("Test-" + UUID.randomUUID(), null);
        String barcode = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
        productId = products.create(new ProductData("Test ghee", barcode, categoryId, SaleUnit.PIECE,
                new BigDecimal("950.00"), null, true));
        orderId = purchaseOrders.create(new PurchaseOrderData(supplierId, "Test order",
                List.of(new OrderLineData(productId, new BigDecimal("12"), new BigDecimal("820.00")))));
    }

    @AfterEach
    void removeTestData() {
        orderRepository.deleteById(orderId);
        batchRepository.deleteAll(batchRepository.findForProduct(productId));
        productRepository.deleteById(productId);
        categories.delete(categoryId);
        supplierRepository.deleteById(supplierId);
    }

    private Long lineId() {
        return purchaseOrders.get(orderId).lines().get(0).lineId();
    }

    private void receive(String quantity, String batchNumber) {
        purchaseOrders.receive(orderId, List.of(new DeliveryLineData(lineId(), new BigDecimal(quantity), batchNumber, EXPIRY)));
    }

    @Test
    void anOrderGoesFromDraftToClosedAndItsDeliveriesBecomeStock() {
        PurchaseOrderDetail draft = purchaseOrders.get(orderId);
        assertThat(draft.status()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(draft.total()).isEqualByComparingTo("9840.00");
        assertThat(draft.lines()).singleElement().satisfies(line -> assertThat(line.productName()).isEqualTo("Test ghee"));

        purchaseOrders.submit(orderId);
        receive("5", "GHEE-1");
        assertThat(purchaseOrders.get(orderId).status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);

        receive("7", "GHEE-2");
        PurchaseOrderDetail received = purchaseOrders.get(orderId);
        assertThat(received.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(received.lines().get(0).outstanding()).isEqualByComparingTo("0");

        List<StockBatchSummary> batches = stock.forProduct(productId);
        assertThat(batches).extracting(StockBatchSummary::batchNumber).containsExactlyInAnyOrder("GHEE-1", "GHEE-2");
        assertThat(batches).allSatisfy(batch -> {
            assertThat(batch.cost()).isEqualByComparingTo("820.00");
            assertThat(batch.expiryDate()).isEqualTo(EXPIRY);
        });
        assertThat(products.get(productId).totalStock()).isEqualByComparingTo("12");

        purchaseOrders.close(orderId);
        assertThat(purchaseOrders.get(orderId).status()).isEqualTo(PurchaseOrderStatus.CLOSED);
    }

    @Test
    void aRefusedDeliveryLeavesTheOrderAndStockUnchanged() {
        purchaseOrders.submit(orderId);

        assertThatThrownBy(() -> receive("13", "GHEE-1")).hasMessage("Only 12 is still to arrive on this line");

        assertThat(purchaseOrders.get(orderId).lines().get(0).receivedQuantity()).isEqualByComparingTo("0");
        assertThat(stock.forProduct(productId)).isEmpty();
    }

    @Test
    void aDraftCannotBeReceivedOrClosed() {
        assertThatThrownBy(() -> receive("1", "GHEE-1")).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> purchaseOrders.close(orderId)).isInstanceOf(IllegalStateTransitionException.class);

        purchaseOrders.cancel(orderId);
        assertThat(purchaseOrders.get(orderId).status()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    }

    @Test
    void theListCanBeFilteredByStatus() {
        purchaseOrders.submit(orderId);

        // The list reads the replica, which can be a moment behind.
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(purchaseOrders.search(PurchaseOrderStatus.SUBMITTED, PageRequest.of(0, 100)).getContent())
                        .anySatisfy(summary -> {
                            assertThat(summary.id()).isEqualTo(orderId);
                            assertThat(summary.lineCount()).isEqualTo(1);
                            assertThat(summary.total()).isEqualByComparingTo("9840.00");
                        }));
        assertThat(purchaseOrders.search(PurchaseOrderStatus.DRAFT, PageRequest.of(0, 100)).getContent())
                .noneMatch(summary -> summary.id().equals(orderId));
    }
}
