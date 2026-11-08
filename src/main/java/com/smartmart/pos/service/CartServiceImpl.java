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
import com.smartmart.pos.domain.CartHistory;
import com.smartmart.pos.domain.CartLine;
import com.smartmart.pos.domain.CartRuleException;
import com.smartmart.pos.domain.CartSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CartServiceImpl implements CartService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final Cart cart;
    private final CartHistory history;
    private final ProductService products;
    private final StoreSettings settings;

    // Promotions and discounts arrive with SCRUM-30.
    @Override
    public CartView view() {
        BigDecimal subtotal = cart.subtotal();
        List<CartLineView> lines = cart.lines().stream().map(this::viewOf).toList();
        return new CartView(lines, subtotal, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                subtotal, vatIncludedIn(subtotal), !history.isEmpty());
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
        CartLine line = newLine(product, unit, quantity);
        recordingForUndo(() -> cart.add(line));
        return Optional.empty();
    }

    // A refused weight names the product, so the cashier can retype it without scanning again.
    private CartLine newLine(ProductSummary product, SaleUnit unit, BigDecimal quantity) {
        try {
            return new CartLine(product.id(), product.name(), product.barcode(), unit, product.price(), quantity);
        } catch (CartRuleException refused) {
            if (unit != SaleUnit.KG) {
                throw refused;
            }
            throw new WeightRejectedException(refused.getMessage(), new WeightPrompt(product.barcode(), product.name()));
        }
    }

    @Override
    public void changeQuantity(Long lineId, BigDecimal quantity) {
        recordingForUndo(() -> cart.changeQuantity(lineId, quantity));
    }

    @Override
    public void remove(Long lineId) {
        recordingForUndo(() -> cart.remove(lineId));
    }

    @Override
    public void undo() {
        cart.restore(history.pop().orElseThrow(() -> new CartRuleException("Nothing to undo")));
    }

    @Override
    public void clear() {
        cart.clear();
        history.clear();
    }

    // Records only real changes: a refused change throws first, and one that changes nothing is skipped.
    private void recordingForUndo(Runnable change) {
        CartSnapshot before = cart.snapshot();
        change.run();
        if (!before.equals(cart.snapshot())) {
            history.push(before);
        }
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
