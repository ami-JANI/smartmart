package com.smartmart.purchasing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;
import com.smartmart.inventory.service.CategoryService;
import com.smartmart.inventory.service.ProductData;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.purchasing.domain.PurchaseOrderStatus;
import com.smartmart.purchasing.repository.PurchaseOrderRepository;
import com.smartmart.purchasing.repository.SupplierRepository;
import com.smartmart.purchasing.service.OrderLineData;
import com.smartmart.purchasing.service.PurchaseOrderData;
import com.smartmart.purchasing.service.PurchaseOrderService;
import com.smartmart.purchasing.service.SupplierData;
import com.smartmart.purchasing.service.SupplierService;

/** The purchase order pages against the databases from compose.yaml (SCRUM-54). */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseOrderPageTests {

    private static final RequestPostProcessor STAFF = user("test-stock").roles("INVENTORY_STAFF");
    private static final RequestPostProcessor MANAGER = user("test-manager").roles("MANAGER");
    private static final RequestPostProcessor ADMIN = user("test-admin").roles("ADMIN");

    private final MockMvc mvc;
    private final PurchaseOrderService purchaseOrders;
    private final SupplierService suppliers;
    private final ProductService products;
    private final CategoryService categories;
    private final PurchaseOrderRepository orderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final List<Long> orderIds = new ArrayList<>();
    private String supplierName;
    private Long supplierId;
    private Long categoryId;
    private Long productId;
    private Long orderId;

    @Autowired
    PurchaseOrderPageTests(MockMvc mvc, PurchaseOrderService purchaseOrders, SupplierService suppliers,
                           ProductService products, CategoryService categories, PurchaseOrderRepository orderRepository,
                           SupplierRepository supplierRepository, ProductRepository productRepository,
                           StockBatchRepository batchRepository) {
        this.mvc = mvc;
        this.purchaseOrders = purchaseOrders;
        this.suppliers = suppliers;
        this.products = products;
        this.categories = categories;
        this.orderRepository = orderRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    @BeforeEach
    void createSupplierProductAndDraft() {
        supplierName = "Test supplier " + UUID.randomUUID();
        supplierId = suppliers.create(new SupplierData(supplierName, null, "01700000000", null, null, true));
        categoryId = categories.create("Test-" + UUID.randomUUID(), null);
        String barcode = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
        productId = products.create(new ProductData("Test mustard oil", barcode, categoryId, SaleUnit.PIECE,
                new BigDecimal("250.00"), null, true));
        orderId = purchaseOrders.create(new PurchaseOrderData(supplierId, "Test order",
                List.of(new OrderLineData(productId, new BigDecimal("10"), new BigDecimal("200.00")))));
        orderIds.add(orderId);
    }

    @AfterEach
    void removeTestData() {
        orderIds.forEach(orderRepository::deleteById);
        batchRepository.deleteAll(batchRepository.findForProduct(productId));
        productRepository.deleteById(productId);
        categories.delete(categoryId);
        supplierRepository.deleteById(supplierId);
    }

    private String orderUrl() {
        return "/purchase-orders/" + orderId;
    }

    @Test
    void staffSeeTheListWithANewOrderButton() throws Exception {
        mvc.perform(get("/purchase-orders").with(STAFF))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New purchase order")))
                .andExpect(content().string(containsString("PO-%06d".formatted(orderId))));
        mvc.perform(get("/purchase-orders").with(MANAGER))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("New purchase order"))));
    }

    @Test
    void staffRaiseADraftFromTheForm() throws Exception {
        String location = mvc.perform(post("/purchase-orders").with(STAFF).with(csrf())
                        .param("supplierId", supplierId.toString())
                        .param("note", "From the page")
                        .param("lines[0].productId", productId.toString())
                        .param("lines[0].quantity", "4")
                        .param("lines[0].unitCost", "199.50")
                        .param("lines[1].productId", "")
                        .param("lines[1].quantity", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Draft purchase order raised"))
                .andReturn().getResponse().getRedirectedUrl();
        Long created = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        orderIds.add(created);

        assertThat(purchaseOrders.get(created).status()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(purchaseOrders.get(created).total()).isEqualByComparingTo("798.00");
    }

    @Test
    void anOrderWithoutProductsIsRefusedAboveTheLines() throws Exception {
        mvc.perform(post("/purchase-orders").with(STAFF).with(csrf())
                        .param("supplierId", supplierId.toString())
                        .param("lines[0].productId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Add at least one product")));
    }

    @Test
    void addRowsShowsMoreEmptyRowsWithoutSaving() throws Exception {
        mvc.perform(post("/purchase-orders").with(STAFF).with(csrf())
                        .param("addRows", "true")
                        .param("lines[0].productId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Product, row 6")));
    }

    @Test
    void onlyAManagerSeesSubmitOnADraft() throws Exception {
        mvc.perform(get(orderUrl()).with(MANAGER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submit (approve)")))
                .andExpect(content().string(containsString(supplierName)));
        mvc.perform(get(orderUrl()).with(STAFF))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Submit (approve)"))))
                .andExpect(content().string(not(containsString("Receive a delivery"))));
    }

    @Test
    void aSubmittedOrderIsReceivedIntoStockFromThePage() throws Exception {
        mvc.perform(post(orderUrl() + "/submit").with(MANAGER).with(csrf()))
                .andExpect(redirectedUrl(orderUrl()))
                .andExpect(flash().attribute("message", "Purchase order submitted"));
        mvc.perform(get(orderUrl() + "/receive").with(STAFF))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test mustard oil")));
        Long lineId = purchaseOrders.get(orderId).lines().get(0).lineId();

        mvc.perform(post(orderUrl() + "/receive").with(STAFF).with(csrf())
                        .param("lines[0].lineId", lineId.toString())
                        .param("lines[0].quantity", "10")
                        .param("lines[0].batchNumber", "PO-TEST-1")
                        .param("lines[0].expiryDate", LocalDate.now().plusMonths(6).toString()))
                .andExpect(redirectedUrl(orderUrl()))
                .andExpect(flash().attribute("message", "Delivery received into stock"));

        assertThat(purchaseOrders.get(orderId).status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(products.get(productId).totalStock()).isEqualByComparingTo("10");
    }

    @Test
    void receivingMoreThanOutstandingIsRefusedAndNothingIsSaved() throws Exception {
        purchaseOrders.submit(orderId);
        Long lineId = purchaseOrders.get(orderId).lines().get(0).lineId();

        mvc.perform(post(orderUrl() + "/receive").with(STAFF).with(csrf())
                        .param("lines[0].lineId", lineId.toString())
                        .param("lines[0].quantity", "11")
                        .param("lines[0].batchNumber", "PO-TEST-2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert-error")));

        assertThat(purchaseOrders.get(orderId).status()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
    }

    @Test
    void anActionTheStateRefusesComesBackAsAMessage() throws Exception {
        mvc.perform(post(orderUrl() + "/close").with(MANAGER).with(csrf()))
                .andExpect(redirectedUrl(orderUrl()))
                .andExpect(flash().attribute("error", "A draft purchase order cannot be closed"));
    }

    @Test
    void adminSeesSubmitAndCancelButtonsOnDraftOrder() throws Exception {
        mvc.perform(get(orderUrl()).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submit (approve)")))
                .andExpect(content().string(containsString("Cancel order")));
    }

    @Test
    void adminSeesNewPurchaseOrderButtonOnList() throws Exception {
        mvc.perform(get("/purchase-orders").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/purchase-orders/new\"")));
    }
}
