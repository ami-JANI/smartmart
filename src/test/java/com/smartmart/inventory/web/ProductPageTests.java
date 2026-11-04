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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.service.CategoryService;
import com.smartmart.inventory.service.ProductData;
import com.smartmart.inventory.service.ProductService;

/**
 * Runs the real pages, services and tables against the two databases from
 * compose.yaml. Lists read from the replica, so checks of what they show wait
 * for replication, polling on the test thread to keep its mock user. Each test
 * removes what it created.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@WithMockUser(roles = "INVENTORY_STAFF")
class ProductPageTests {

    private final MockMvc mvc;
    private final ProductService products;
    private final ProductRepository productRepository;
    private final CategoryService categories;
    private final List<String> createdBarcodes = new ArrayList<>();
    private Long categoryId;
    private String categoryName;

    @Autowired
    ProductPageTests(MockMvc mvc, ProductService products, ProductRepository productRepository, CategoryService categories) {
        this.mvc = mvc;
        this.products = products;
        this.productRepository = productRepository;
        this.categories = categories;
    }

    @BeforeEach
    void createCategory() {
        categoryName = "Test-" + UUID.randomUUID();
        categoryId = categories.create(categoryName, null);
    }

    @AfterEach
    void removeTestData() {
        productRepository.findAll().stream()
                .filter(product -> createdBarcodes.contains(product.getBarcode()))
                .forEach(productRepository::delete);
        categories.delete(categoryId);
    }

    private String newBarcode() {
        String barcode = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
        createdBarcodes.add(barcode);
        return barcode;
    }

    private Long createProduct(String name, String barcode, String price) {
        return products.create(new ProductData(name, barcode, categoryId, SaleUnit.PIECE, new BigDecimal(price), null, true));
    }

    private void awaitList(String query, String expectedText) {
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/products" + query))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString(expectedText))));
    }

    private MockHttpServletRequestBuilder validForm(String url, String name, String barcode) {
        return post(url).with(csrf())
                .param("name", name)
                .param("barcode", barcode)
                .param("categoryId", categoryId.toString())
                .param("saleUnit", "PIECE")
                .param("price", "75.50")
                .param("lowStockThreshold", "")
                .param("active", "true");
    }

    @Test
    void creatingAProductRedirectsAndItAppearsInTheList() throws Exception {
        String name = "Miniket-" + UUID.randomUUID();

        mvc.perform(validForm("/products", name, newBarcode()))
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("message", "Product created"));

        awaitList("?q=" + name, name);
        mvc.perform(get("/products?q=" + name))
                .andExpect(content().string(containsString("75.50")))
                .andExpect(content().string(containsString(categoryName)));
    }

    @Test
    void newFormRenders() throws Exception {
        mvc.perform(get("/products/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New product")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void brokenFieldsShowTheirMessages() throws Exception {
        mvc.perform(post("/products").with(csrf())
                        .param("name", "")
                        .param("barcode", "12")
                        .param("categoryId", "")
                        .param("saleUnit", "PIECE")
                        .param("price", "0")
                        .param("lowStockThreshold", "-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Name is required")))
                .andExpect(content().string(containsString("Barcode must be 4 to 20 digits")))
                .andExpect(content().string(containsString("Choose a category")))
                .andExpect(content().string(containsString("Price must be greater than 0")))
                .andExpect(content().string(containsString("Threshold cannot be negative")));
    }

    @Test
    void textInANumberFieldShowsAFriendlyMessage() throws Exception {
        mvc.perform(post("/products").with(csrf()).param("name", "Oil").param("barcode", "1234")
                        .param("categoryId", categoryId.toString()).param("saleUnit", "PIECE").param("price", "abc"))
                .andExpect(content().string(containsString("Enter a number")));
    }

    @Test
    void duplicateBarcodeShowsTheFieldError() throws Exception {
        String barcode = newBarcode();
        createProduct("First", barcode, "10");

        mvc.perform(validForm("/products", "Second", barcode))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Barcode already in use")));
    }

    @Test
    void decimalThresholdOnAPieceProductShowsTheFieldError() throws Exception {
        mvc.perform(post("/products").with(csrf()).param("name", "Oil").param("barcode", newBarcode())
                        .param("categoryId", categoryId.toString()).param("saleUnit", "PIECE")
                        .param("price", "10").param("lowStockThreshold", "2.5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("needs a whole number")));
    }

    @Test
    void editFormShowsTheProductAndSavingChangesIt() throws Exception {
        String barcode = newBarcode();
        Long id = createProduct("Before", barcode, "10");

        mvc.perform(get("/products/" + id + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit product")))
                .andExpect(content().string(containsString("Before")));

        mvc.perform(validForm("/products/" + id, "After", barcode))
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("message", "Product saved"));

        mvc.perform(get("/products/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("After")))
                .andExpect(content().string(containsString("75.50")));
    }

    @Test
    void missingProductShowsNotFound() throws Exception {
        mvc.perform(get("/products/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void searchFindsPartOfANameOrBarcodeIgnoringCase() throws Exception {
        String marker = UUID.randomUUID().toString();
        String barcode = newBarcode();
        createProduct("Rice " + marker, barcode, "10");

        awaitList("?q=RICE&category=" + categoryId, "Rice " + marker);
        awaitList("?q=" + barcode.substring(2, 9) + "&category=" + categoryId, "Rice " + marker);
        mvc.perform(get("/products?q=" + marker + "-nothing"))
                .andExpect(content().string(not(containsString("Rice " + marker))));
    }

    @Test
    void searchTreatsPercentAsPlainText() throws Exception {
        String marker = UUID.randomUUID().toString();
        createProduct("Rice " + marker, newBarcode(), "10");

        awaitList("?category=" + categoryId, marker);
        mvc.perform(get("/products").param("q", "%").param("category", categoryId.toString()))
                .andExpect(content().string(not(containsString(marker))));
    }

    @Test
    void categoryFilterSortAndPagingWork() throws Exception {
        createProduct("Cheap", newBarcode(), "5");
        createProduct("Dear", newBarcode(), "500");

        awaitList("?category=" + categoryId, "2 products");

        String list = mvc.perform(get("/products?category=" + categoryId + "&sort=price,desc"))
                .andReturn().getResponse().getContentAsString();
        assertThat(list.indexOf("Dear")).isLessThan(list.indexOf("Cheap"));

        mvc.perform(get("/products?category=" + categoryId + "&size=1&page=1&sort=price,asc"))
                .andExpect(content().string(containsString("Dear")))
                .andExpect(content().string(not(containsString(">Cheap<"))));
    }

    @Test
    void unknownSortFieldFallsBackToName() throws Exception {
        mvc.perform(get("/products?sort=password,asc")).andExpect(status().isOk());
    }

    @Test
    void categoryWithAProductCannotBeDeleted() throws Exception {
        createProduct("Oil", newBarcode(), "10");

        mvc.perform(post("/categories/" + categoryId + "/delete").with(csrf()))
                .andExpect(flash().attribute("error", "Cannot delete: this category has products or child categories"));
    }
}
