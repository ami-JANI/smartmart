package com.smartmart.pos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.auth.service.UserService;
import com.smartmart.auth.service.UserSummary;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.pos.domain.Discount;
import com.smartmart.pos.domain.Sale;
import com.smartmart.pos.repository.DiscountRepository;
import com.smartmart.pos.repository.SaleRepository;

class InvoiceServiceImplTest {

    private final SaleRepository sales = mock(SaleRepository.class);
    private final DiscountRepository discounts = mock(DiscountRepository.class);
    private final ProductService products = mock(ProductService.class);
    private final UserService users = mock(UserService.class);
    private final InvoiceService service = new InvoiceServiceImpl(sales, discounts, products, users);

    private Sale sale(Long discountId) {
        Sale sale = new Sale(7L, discountId, Instant.parse("2026-11-17T08:30:00Z"), new BigDecimal("188.50"),
                new BigDecimal("50.00"), new BigDecimal("138.50"), new BigDecimal("18.07"));
        sale.addLine(3L, new BigDecimal("1.250"), new BigDecimal("78.00"), new BigDecimal("97.50"));
        sale.recordPayment("CASH", new BigDecimal("138.50"), new BigDecimal("150.00"), new BigDecimal("11.50"));
        ReflectionTestUtils.setField(sale, "id", 42L);
        return sale;
    }

    private void knownCashierAndProduct() {
        when(users.get(7L)).thenReturn(new UserSummary(7L, "cashier", "Rahim Uddin", List.of("CASHIER"), true));
        when(products.get(3L)).thenReturn(new ProductSummary(3L, "Miniket rice", "2000000000011", 1L, "Rice",
                new BigDecimal("78.00"), "KG", null, true, new BigDecimal("10"), false));
    }

    @Test
    void invoiceHasEveryFieldOfTheSale() {
        Discount discount = mock(Discount.class);
        when(discount.getName()).thenReturn("Opening week");
        when(discounts.findById(5L)).thenReturn(Optional.of(discount));
        when(sales.findById(42L)).thenReturn(Optional.of(sale(5L)));
        knownCashierAndProduct();

        InvoiceView invoice = service.invoice(42L);

        assertThat(invoice.number()).isEqualTo("INV-000042");
        assertThat(invoice.cashierName()).isEqualTo("Rahim Uddin");
        assertThat(invoice.discountName()).isEqualTo("Opening week");
        assertThat(invoice.total()).isEqualByComparingTo("138.50");
        assertThat(invoice.vatIncluded()).isEqualByComparingTo("18.07");
        assertThat(invoice.cashReceived()).isEqualByComparingTo("150.00");
        assertThat(invoice.changeDue()).isEqualByComparingTo("11.50");
        assertThat(invoice.promotionSavings()).isEqualByComparingTo("0");
        assertThat(invoice.lines()).singleElement().satisfies(line -> {
            assertThat(line.productName()).isEqualTo("Miniket rice");
            assertThat(line.saleUnit()).isEqualTo("KG");
            assertThat(line.quantity()).isEqualByComparingTo("1.250");
            assertThat(line.lineTotal()).isEqualByComparingTo("97.50");
            assertThat(line.free()).isFalse();
        });
    }

    @Test
    void saleWithoutADiscountHasNoDiscountName() {
        when(sales.findById(42L)).thenReturn(Optional.of(sale(null)));
        knownCashierAndProduct();

        assertThat(service.invoice(42L).discountName()).isNull();
    }

    @Test
    void unknownSaleIsRefused() {
        when(sales.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invoice(99L)).isInstanceOf(SaleNotFoundException.class);
    }
}
