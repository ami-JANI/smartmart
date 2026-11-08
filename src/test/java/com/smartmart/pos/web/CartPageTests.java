package com.smartmart.pos.web;

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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.service.CategoryService;
import com.smartmart.inventory.service.ProductData;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.pos.service.WeightPrompt;

/**
 * Runs the real cart screen against the two databases from compose.yaml, with
 * one session per cashier. Barcode lookups read from the replica, so the first
 * scan of a new product waits for replication. Each test removes what it created.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@WithMockUser(roles = "CASHIER")
class CartPageTests {

    private final MockMvc mvc;
    private final TransactionTemplate writes;
    private final ProductService products;
    private final ProductRepository productRepository;
    private final CategoryService categories;
    private final MockHttpSession session = new MockHttpSession();
    private Long categoryId;

    @Autowired
    CartPageTests(MockMvc mvc, ProductService products, ProductRepository productRepository, CategoryService categories,
                  TransactionTemplate writes) {
        this.mvc = mvc;
        this.products = products;
        this.productRepository = productRepository;
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
                    .forEach(productRepository::delete);
            categories.delete(categoryId);
        });
    }

    private String barcode() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
    }

    private Long product(String name, String barcode, SaleUnit unit, String price, boolean active) {
        return products.create(new ProductData(name, barcode, categoryId, unit, new BigDecimal(price), null, active));
    }

    private ResultActions scan(String barcode) throws Exception {
        return mvc.perform(post("/pos/cart/items").session(session).with(csrf()).param("barcode", barcode));
    }

    private void awaitAdded(String barcode) {
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                scan(barcode).andExpect(flash().attributeCount(0)));
    }

    private ResultActions cartPage() throws Exception {
        return mvc.perform(get("/pos").session(session)).andExpect(status().isOk());
    }

    @Test
    void emptyCartRenders() throws Exception {
        cartPage()
                .andExpect(content().string(containsString("The cart is empty")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("data-autofocus")));
    }

    @Test
    void scanningAddsALineAndTotals() throws Exception {
        String barcode = barcode();
        product("Soybean Oil", barcode, SaleUnit.PIECE, "115.00", true);

        awaitAdded(barcode);
        scan(barcode).andExpect(redirectedUrl("/pos"));

        cartPage()
                .andExpect(content().string(containsString("Soybean Oil")))
                .andExpect(content().string(containsString("230.00")))
                .andExpect(content().string(containsString("30.00")))
                .andExpect(content().string(not(containsString("The cart is empty"))));
    }

    @Test
    void unknownBarcodeShowsAnError() throws Exception {
        scan("0000").andExpect(redirectedUrl("/pos"))
                .andExpect(flash().attribute("error", "Product not found"));
    }

    @Test
    void inactiveProductCannotBeScanned() throws Exception {
        String barcode = barcode();
        product("Old Stock", barcode, SaleUnit.PIECE, "10", false);

        scan(barcode).andExpect(flash().attribute("error", "Product not found"));
    }

    @Test
    void weighedProductPromptsThenAddsTheWeight() throws Exception {
        String barcode = barcode();
        product("Loose Rice", barcode, SaleUnit.KG, "75.50", true);

        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                scan(barcode).andExpect(flash().attribute("weightPrompt", new WeightPrompt(barcode, "Loose Rice"))));

        mvc.perform(get("/pos").session(session).flashAttr("weightPrompt", new WeightPrompt(barcode, "Loose Rice")))
                .andExpect(content().string(containsString("Weight in kg for")));

        // A refused weight keeps the weight box open for the same product (SCRUM-49).
        mvc.perform(post("/pos/cart/items").session(session).with(csrf()).param("barcode", barcode).param("weight", "0"))
                .andExpect(flash().attribute("error", "Weight must be greater than 0, with at most 3 decimals"))
                .andExpect(flash().attribute("weightPrompt", new WeightPrompt(barcode, "Loose Rice")));

        mvc.perform(post("/pos/cart/items").session(session).with(csrf()).param("barcode", barcode).param("weight", "1.250"))
                .andExpect(flash().attributeCount(0));

        cartPage()
                .andExpect(content().string(containsString("Loose Rice")))
                .andExpect(content().string(containsString("94.38")));
    }

    @Test
    void quantityCanBeChangedAndABadOneIsRefused() throws Exception {
        String barcode = barcode();
        Long id = product("Soap", barcode, SaleUnit.PIECE, "10.00", true);
        awaitAdded(barcode);

        mvc.perform(post("/pos/cart/items/" + id + "/quantity").session(session).with(csrf()).param("quantity", "4"))
                .andExpect(redirectedUrl("/pos"));
        cartPage().andExpect(content().string(containsString("40.00")));

        mvc.perform(post("/pos/cart/items/" + id + "/quantity").session(session).with(csrf()).param("quantity", "0"))
                .andExpect(flash().attribute("error", "Quantity must be a whole number of at least 1"));
        mvc.perform(post("/pos/cart/items/" + id + "/quantity").session(session).with(csrf()).param("quantity", "abc"))
                .andExpect(flash().attribute("error", "Enter a valid number"));
        cartPage().andExpect(content().string(containsString("40.00")));
    }

    @Test
    void removingALineEmptiesTheCart() throws Exception {
        String barcode = barcode();
        Long id = product("Soap", barcode, SaleUnit.PIECE, "10.00", true);
        awaitAdded(barcode);

        mvc.perform(post("/pos/cart/items/" + id + "/remove").session(session).with(csrf()))
                .andExpect(redirectedUrl("/pos"));

        cartPage().andExpect(content().string(containsString("The cart is empty")));
    }

    @Test
    void undoButtonIsDisabledUntilThereIsAChange() throws Exception {
        cartPage().andExpect(content().string(containsString("disabled=\"disabled\">Undo last change")));

        String barcode = barcode();
        product("Soap", barcode, SaleUnit.PIECE, "10.00", true);
        awaitAdded(barcode);

        cartPage().andExpect(content().string(not(containsString("disabled=\"disabled\">Undo last change"))));
    }

    @Test
    void undoReversesTheLastChange() throws Exception {
        String barcode = barcode();
        Long id = product("Soap", barcode, SaleUnit.PIECE, "10.00", true);
        awaitAdded(barcode);
        mvc.perform(post("/pos/cart/items/" + id + "/remove").session(session).with(csrf()));
        cartPage().andExpect(content().string(containsString("The cart is empty")));

        mvc.perform(post("/pos/cart/undo").session(session).with(csrf()))
                .andExpect(redirectedUrl("/pos"))
                .andExpect(flash().attribute("message", "Last change undone"));

        cartPage().andExpect(content().string(containsString("Soap")));
    }

    @Test
    void undoWithNothingToUndoShowsAnError() throws Exception {
        mvc.perform(post("/pos/cart/undo").session(session).with(csrf()))
                .andExpect(redirectedUrl("/pos"))
                .andExpect(flash().attribute("error", "Nothing to undo"));
    }

    @Test
    void eachSessionHasItsOwnCart() throws Exception {
        String barcode = barcode();
        product("Soap", barcode, SaleUnit.PIECE, "10.00", true);
        awaitAdded(barcode);

        mvc.perform(get("/pos").session(new MockHttpSession()))
                .andExpect(content().string(containsString("The cart is empty")));
    }
}
