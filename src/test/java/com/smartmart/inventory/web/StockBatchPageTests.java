package com.smartmart.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;
import com.smartmart.inventory.service.CategoryService;
import com.smartmart.inventory.service.ProductData;
import com.smartmart.inventory.service.ProductService;

/**
 * Runs the real product page against the two databases from compose.yaml.
 * The add-batch form shows only for inventory staff, so the test logs in with
 * that role. Each test removes what it created.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@WithMockUser(roles = "INVENTORY_STAFF")
class StockBatchPageTests {

    private final MockMvc mvc;
    private final TransactionTemplate writes;
    private final ProductService products;
    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final CategoryService categories;
    private Long categoryId;

    @Autowired
    StockBatchPageTests(MockMvc mvc, ProductService products, ProductRepository productRepository,
                        StockBatchRepository batchRepository, CategoryService categories, TransactionTemplate writes) {
        this.mvc = mvc;
        this.products = products;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.categories = categories;
        this.writes = writes;
    }

    @BeforeEach
    void createCategory() {
        categoryId = categories.create("Test-" + UUID.randomUUID(), null);
    }

    @AfterEach
    void removeTestData() {
        // One read-write transaction, so the reads below go to the primary and see rows
        // created a moment ago; a read-only read could reach a replica that is behind.
        writes.executeWithoutResult(status -> {
            productRepository.findAll().stream()
                    .filter(product -> product.getCategory().getId().equals(categoryId))
                    .forEach(product -> {
                        batchRepository.deleteAll(batchRepository.findForProduct(product.getId()));
                        productRepository.delete(product);
                    });
            categories.delete(categoryId);
        });
    }

    private Long product(SaleUnit unit, String threshold) {
        String barcode = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
        return products.create(new ProductData("Item-" + UUID.randomUUID(), barcode, categoryId, unit,
                BigDecimal.TEN, threshold == null ? null : new BigDecimal(threshold), true));
    }

    private void addBatch(Long productId, String number, String quantity, String expiry) throws Exception {
        mvc.perform(post("/products/" + productId + "/batches").with(csrf())
                        .param("batchNumber", number)
                        .param("quantity", quantity)
                        .param("cost", "40.00")
                        .param("expiryDate", expiry))
                .andExpect(redirectedUrl("/products/" + productId))
                .andExpect(flash().attribute("message", "Batch added"));
    }

    private String page(Long productId) throws Exception {
        return mvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void pageShowsTheAddBatchForm() throws Exception {
        assertThat(page(product(SaleUnit.PIECE, null)))
                .contains("Add a batch", "name=\"batchNumber\"", "name=\"_csrf\"", "No batches yet");
    }

    @Test
    void addedBatchesShowStraightAwayWithTotalAndSoonestExpiryFirst() throws Exception {
        Long id = product(SaleUnit.PIECE, null);

        addBatch(id, "LATER", "5", "2031-06-01");
        addBatch(id, "NO-DATE", "2", "");
        addBatch(id, "SOONEST", "3", "2030-01-15");

        String html = page(id);
        assertThat(html.indexOf("SOONEST")).isLessThan(html.indexOf("LATER"));
        assertThat(html.indexOf("LATER")).isLessThan(html.indexOf("NO-DATE"));
        assertThat(html).contains("<strong>10</strong>", "No expiry date", "15 Jan 2030");
    }

    @Test
    void expiredBatchIsMarked() throws Exception {
        Long id = product(SaleUnit.PIECE, null);

        addBatch(id, "OLD", "5", LocalDate.now().minusDays(1).toString());

        assertThat(page(id)).contains("Expired");
    }

    @Test
    void weighedQuantityKeepsItsDecimals() throws Exception {
        Long id = product(SaleUnit.KG, null);

        addBatch(id, "LOOSE", "1.250", "");

        assertThat(page(id)).contains("<strong>1.250</strong>");
    }

    @Test
    void brokenFieldsShowTheirMessagesOnTheProductPage() throws Exception {
        mvc.perform(post("/products/" + product(SaleUnit.PIECE, null) + "/batches").with(csrf())
                        .param("batchNumber", "")
                        .param("quantity", "0")
                        .param("cost", "-1")
                        .param("expiryDate", "not-a-date"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Batch number is required")))
                .andExpect(content().string(containsString("Quantity must be greater than 0")))
                .andExpect(content().string(containsString("Cost cannot be negative")))
                .andExpect(content().string(containsString("Enter a valid date")));
    }

    @Test
    void decimalQuantityOnAPieceProductShowsTheFieldError() throws Exception {
        mvc.perform(post("/products/" + product(SaleUnit.PIECE, null) + "/batches").with(csrf())
                        .param("batchNumber", "B")
                        .param("quantity", "2.5")
                        .param("cost", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("needs a whole number")));
    }

    @Test
    void batchForAMissingProductIsNotFound() throws Exception {
        mvc.perform(post("/products/999999999/batches").with(csrf())
                        .param("batchNumber", "B").param("quantity", "1").param("cost", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lowStockFollowsTheThresholdAndTheFilterUsesIt() throws Exception {
        Long atThreshold = product(SaleUnit.PIECE, "5");
        Long aboveThreshold = product(SaleUnit.PIECE, "5");
        Long noBatchesUsesDefault = product(SaleUnit.PIECE, null);
        Long aboveDefault = product(SaleUnit.PIECE, null);
        addBatch(atThreshold, "A", "5", "");
        addBatch(aboveThreshold, "B", "6", "");
        addBatch(aboveDefault, "C", "11", "");

        assertThat(page(atThreshold)).contains("Low stock");
        assertThat(page(aboveThreshold)).doesNotContain("Low stock");
        assertThat(page(noBatchesUsesDefault)).contains("Low stock");
        assertThat(page(aboveDefault)).doesNotContain("Low stock");

        String all = "/products?category=" + categoryId;
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get(all + "&lowStock=true"))
                        .andExpect(content().string(containsString("2 products")))
                        .andExpect(content().string(containsString("/products/" + atThreshold)))
                        .andExpect(content().string(containsString("/products/" + noBatchesUsesDefault)))
                        .andExpect(content().string(not(containsString("/products/" + aboveThreshold + "\""))))
                        .andExpect(content().string(not(containsString("/products/" + aboveDefault + "\"")))));
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get(all))
                        .andExpect(content().string(containsString("4 products"))));
    }
}
