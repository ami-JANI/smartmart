package com.smartmart.pos.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.auth.service.UserService;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.pos.domain.Discount;
import com.smartmart.pos.domain.Payment;
import com.smartmart.pos.domain.Sale;
import com.smartmart.pos.domain.SaleLine;
import com.smartmart.pos.repository.DiscountRepository;
import com.smartmart.pos.repository.SaleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class InvoiceServiceImpl implements InvoiceService {

    private static final BigDecimal NO_SAVINGS = new BigDecimal("0.00");

    private final SaleRepository sales;
    private final DiscountRepository discounts;
    private final ProductService products;
    private final UserService users;

    // Read-write on purpose: the invoice is shown straight after the sale, so it
    // must read the primary, not a replica that may be behind (GUIDELINES 8.2).
    @Override
    @Transactional
    public InvoiceView invoice(Long saleId) {
        Sale sale = sales.findById(saleId).orElseThrow(() -> new SaleNotFoundException(saleId));
        Payment payment = sale.getPayment();
        List<InvoiceView.Line> lines = sale.getLines().stream().map(this::lineOf).toList();
        return new InvoiceView(sale.invoiceNumber(), sale.getSoldAt(), users.get(sale.getCashierId()).fullName(),
                lines, sale.getSubtotal(), NO_SAVINGS, discountName(sale.getDiscountId()), sale.getDiscountAmount(),
                sale.getTotal(), sale.getVatIncluded(), payment.getCashReceived(), payment.getChangeDue());
    }

    private InvoiceView.Line lineOf(SaleLine line) {
        ProductSummary product = products.get(line.getProductId());
        return new InvoiceView.Line(product.name(), product.saleUnit(), line.getQuantity(), line.getUnitPrice(),
                line.getLineTotal(), false);
    }

    // A discount that has ended since the sale still shows its name.
    private String discountName(Long discountId) {
        return discountId == null ? null : discounts.findById(discountId).map(Discount::getName).orElse(null);
    }
}
