package com.smartmart.pos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.repository.UserRepository;
import com.smartmart.auth.service.AccountDetails;
import com.smartmart.auth.service.AccountDetailsService;
import com.smartmart.auth.service.UserData;
import com.smartmart.auth.service.UserService;
import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.domain.StockBatch;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;
import com.smartmart.inventory.service.CategoryService;
import com.smartmart.inventory.service.ProductData;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.StockBatchData;
import com.smartmart.inventory.service.StockBatchService;
import com.smartmart.pos.domain.Sale;
import com.smartmart.pos.repository.CustomerRepository;
import com.smartmart.pos.repository.SaleRepository;

/**
 * Completes real cash sales on the two databases from compose.yaml, as a
 * logged-in cashier (FR-POS-9 to FR-POS-12). Reads that check what was saved
 * run in a read-write transaction, so they see the primary. Each test removes
 * what it created.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutPageTests {

    private final MockMvc mvc;
    private final TransactionTemplate writes;
    private final ProductService products;
    private final ProductRepository productRepository;
    private final StockBatchService stock;
    private final StockBatchRepository batchRepository;
    private final CategoryService categories;
    private final UserService userService;
    private final UserRepository userRepository;
    private final AccountDetailsService accounts;
    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private String memberPhone;
    private final MockHttpSession session = new MockHttpSession();
    private Long categoryId;
    private Long productId;
    private String barcode;
    private AccountDetails cashier;

    @Autowired
    CheckoutPageTests(MockMvc mvc, TransactionTemplate writes, ProductService products,
                      ProductRepository productRepository, StockBatchService stock,
                      StockBatchRepository batchRepository, CategoryService categories, UserService userService,
                      UserRepository userRepository, AccountDetailsService accounts, SaleRepository saleRepository,
                      CustomerRepository customerRepository) {
        this.mvc = mvc;
        this.writes = writes;
        this.products = products;
        this.productRepository = productRepository;
        this.stock = stock;
        this.batchRepository = batchRepository;
        this.categories = categories;
        this.userService = userService;
        this.userRepository = userRepository;
        this.accounts = accounts;
        this.saleRepository = saleRepository;
        this.customerRepository = customerRepository;
    }

    @BeforeEach
    void createCashierAndProduct() {
        String username = "cashier-" + UUID.randomUUID().toString().substring(0, 8);
        userService.create(new UserData(username, "Test Cashier", "Cashier-pass-1", Set.of(RoleName.CASHIER)));
        UserDetails details = accounts.loadUserByUsername(username);
        cashier = (AccountDetails) details;
        categoryId = categories.create("Test-" + UUID.randomUUID(), null);
        barcode = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
        productId = products.create(new ProductData("Test soap", barcode, categoryId, SaleUnit.PIECE,
                new BigDecimal("10.00"), null, true));
    }

    @AfterEach
    void removeTestData() {
        writes.executeWithoutResult(status -> {
            saleRepository.deleteAll(salesOfCashier());
            if (memberPhone != null) {
                customerRepository.findByPhone(memberPhone).ifPresent(customerRepository::delete);
            }
            batchRepository.deleteAll(batchRepository.findForProduct(productId));
            productRepository.deleteById(productId);
            categories.delete(categoryId);
            userRepository.deleteById(cashier.getId());
        });
    }

    private MockHttpServletRequestBuilder asCashier(MockHttpServletRequestBuilder request) {
        return request.session(session).with(user(cashier)).with(csrf());
    }

    private void stockOf(int quantity) {
        stock.add(productId, new StockBatchData("B-1", new BigDecimal(quantity), new BigDecimal("7.00"),
                LocalDate.now().plusDays(30)));
    }

    // The first scan waits for the new product to reach the replica.
    private void scanTimes(int times) throws Exception {
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(asCashier(post("/pos/cart/items")).param("barcode", barcode))
                        .andExpect(flash().attributeCount(0)));
        for (int i = 1; i < times; i++) {
            mvc.perform(asCashier(post("/pos/cart/items")).param("barcode", barcode));
        }
    }

    private ResultActions pay(String cash) throws Exception {
        return mvc.perform(asCashier(post("/pos/checkout")).param("cashReceived", cash));
    }

    private List<Sale> salesOfCashier() {
        return saleRepository.findAll().stream().filter(sale -> sale.getCashierId().equals(cashier.getId())).toList();
    }

    private List<Sale> savedSales() {
        return writes.execute(status -> {
            List<Sale> sales = salesOfCashier();
            sales.forEach(sale -> {
                sale.getLines().size();
                sale.getPayment().getChangeDue();
            });
            return sales;
        });
    }

    private BigDecimal stockLeft() {
        return writes.execute(status -> batchRepository.findForProduct(productId).stream()
                .map(StockBatch::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void emptyCartCannotBePaid() throws Exception {
        mvc.perform(asCashier(get("/pos/checkout")))
                .andExpect(redirectedUrl("/pos"))
                .andExpect(flash().attribute("error", "The cart is empty"));
        pay("10").andExpect(redirectedUrl("/pos"))
                .andExpect(flash().attribute("error", "The cart is empty"));
    }

    @Test
    void paymentPageShowsTheAmountToPay() throws Exception {
        stockOf(5);
        scanTimes(2);

        mvc.perform(asCashier(get("/pos/checkout")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cash payment")))
                .andExpect(content().string(containsString("20.00")));
    }

    @Test
    void completedSaleSavesTheSalePaymentAndStockAndClearsTheCart() throws Exception {
        stockOf(2);
        scanTimes(2);

        pay("50").andExpect(redirectedUrlPattern("/pos/invoices/*"));

        List<Sale> sales = savedSales();
        assertThat(sales).hasSize(1);
        Sale sale = sales.get(0);
        assertThat(sale.getTotal()).isEqualByComparingTo("20.00");
        assertThat(sale.getVatIncluded()).isEqualByComparingTo("2.61");
        assertThat(sale.getLines()).singleElement()
                .satisfies(line -> assertThat(line.getQuantity()).isEqualByComparingTo("2"));
        assertThat(sale.getPayment().getCashReceived()).isEqualByComparingTo("50.00");
        assertThat(sale.getPayment().getChangeDue()).isEqualByComparingTo("30.00");
        assertThat(stockLeft()).isEqualByComparingTo("0");
        mvc.perform(asCashier(get("/pos"))).andExpect(content().string(containsString("The cart is empty")));
    }

    @Test
    void invoiceShowsTheSaleStraightAfterPayment() throws Exception {
        stockOf(2);
        scanTimes(2);

        String invoicePage = pay("50").andReturn().getResponse().getRedirectedUrl();

        mvc.perform(asCashier(get(invoicePage)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INV-")))
                .andExpect(content().string(containsString("Test soap")))
                .andExpect(content().string(containsString("Test Cashier")))
                .andExpect(content().string(containsString("30.00")));
    }

    @Test
    void unknownInvoiceIsNotFound() throws Exception {
        mvc.perform(asCashier(get("/pos/invoices/999999999"))).andExpect(status().isNotFound());
    }

    @Test
    void cashBelowTheTotalIsRefusedAndNothingIsSaved() throws Exception {
        stockOf(5);
        scanTimes(2);

        pay("19.99")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cash received is less than the total")));

        assertThat(savedSales()).isEmpty();
        assertThat(stockLeft()).isEqualByComparingTo("5");
        mvc.perform(asCashier(get("/pos"))).andExpect(content().string(containsString("Test soap")));
    }

    @Test
    void missingCashIsAFieldError() throws Exception {
        stockOf(5);
        scanTimes(1);

        pay("").andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter the cash received")));
    }

    @Test
    void notEnoughStockIsRefusedWith409AndNothingIsSaved() throws Exception {
        stockOf(1);
        scanTimes(2);

        pay("100")
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Not enough stock for Test soap")));

        assertThat(savedSales()).isEmpty();
        assertThat(stockLeft()).isEqualByComparingTo("1");
        mvc.perform(asCashier(get("/pos"))).andExpect(content().string(containsString("Test soap")));
    }

    @Test
    void aMemberRegisteredAtTheTillIsRecordedOnTheSale() throws Exception {
        stockOf(1);
        scanTimes(1);
        memberPhone = "0199" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);

        mvc.perform(asCashier(post("/pos/members")).param("name", "Test Member").param("phone", memberPhone)
                        .param("addToSale", "true"))
                .andExpect(redirectedUrl("/pos"));
        mvc.perform(asCashier(get("/pos"))).andExpect(content().string(containsString("Test Member")));
        mvc.perform(asCashier(post("/pos/members")).param("name", "Again").param("phone", memberPhone))
                .andExpect(content().string(containsString("This phone number is already a member")));

        pay("20").andExpect(redirectedUrlPattern("/pos/invoices/*"));

        Long memberId = writes.execute(status -> customerRepository.findByPhone(memberPhone).orElseThrow().getId());
        assertThat(savedSales()).singleElement().satisfies(sale -> assertThat(sale.getCustomerId()).isEqualTo(memberId));
    }

    @Test
    void unknownMemberPhoneIsRefusedOnTheCart() throws Exception {
        mvc.perform(asCashier(post("/pos/cart/member")).param("phone", "0100000000"))
                .andExpect(redirectedUrl("/pos"))
                .andExpect(flash().attribute("error", "No member with that phone number"));
    }
}
