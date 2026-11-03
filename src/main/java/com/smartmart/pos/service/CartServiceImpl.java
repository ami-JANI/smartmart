package com.smartmart.pos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smartmart.common.config.StoreSettings;
import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.pos.domain.Cart;
import com.smartmart.pos.domain.CartLine;
import com.smartmart.pos.domain.CartRuleException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CartServiceImpl implements CartService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final Cart cart;
    private final ProductService products;
    private final StoreSettings settings;

    // Promotions and discounts arrive with SCRUM-30, and undo with SCRUM-29.
    @Override
    public CartView view() {
        BigDecimal subtotal = cart.subtotal();
        List<CartLineView> lines = cart.lines().stream().map(this::viewOf).toList();
        return new CartView(lines, subtotal, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                subtotal, vatIncludedIn(subtotal), false);
    }

    @Override
    public Optional<WeightPrompt> scan(String barcode, BigDecimal weight) {
        ProductSummary product = products.findActiveByBarcode(barcode.strip())
                .orElseThrow(() -> new CartRuleException("Product not found"));
        SaleUnit unit = SaleUnit.valueOf(product.saleUnit());
        if (unit == SaleUnit.KG && weight == null) {
            return Optional.of(new WeightPrompt(product.barcode(), product.name()));
        }
        BigDecimal quantity = unit == SaleUnit.KG ? weight : BigDecimal.ONE;
        cart.add(new CartLine(product.id(), product.name(), product.barcode(), unit, product.price(), quantity));
        return Optional.empty();
    }

    @Override
    public void changeQuantity(Long lineId, BigDecimal quantity) {
        cart.changeQuantity(lineId, quantity);
    }

    @Override
    public void remove(Long lineId) {
        cart.remove(lineId);
    }

    private CartLineView viewOf(CartLine line) {
        return new CartLineView(line.productId(), line.productName(), line.barcode(), line.saleUnit().name(),
                line.unitPrice(), line.quantity(), line.total(), false);
    }

    private BigDecimal vatIncludedIn(BigDecimal total) {
        BigDecimal rate = settings.vatRate();
        return total.multiply(rate).divide(HUNDRED.add(rate), 2, RoundingMode.HALF_UP);
    }
}
