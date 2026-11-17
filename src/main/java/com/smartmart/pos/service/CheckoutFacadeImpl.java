package com.smartmart.pos.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.smartmart.inventory.service.StockBatchService;
import com.smartmart.pos.domain.CartRuleException;
import com.smartmart.pos.domain.Sale;
import com.smartmart.pos.repository.SaleRepository;

/**
 * The checkout facade. The transaction is run through
 * {@link TransactionOperations} rather than {@code @Transactional}, so the
 * cart is cleared only once the commit has succeeded.
 */
@Service
class CheckoutFacadeImpl implements CheckoutFacade {

    private final CartService carts;
    private final PaymentMethod paymentMethod;
    private final StockBatchService stock;
    private final SaleRepository sales;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    CheckoutFacadeImpl(CartService carts, PaymentMethod paymentMethod, StockBatchService stock, SaleRepository sales,
                       TransactionOperations transactions) {
        this(carts, paymentMethod, stock, sales, transactions, Clock.systemUTC());
    }

    CheckoutFacadeImpl(CartService carts, PaymentMethod paymentMethod, StockBatchService stock, SaleRepository sales,
                       TransactionOperations transactions, Clock clock) {
        this.carts = carts;
        this.paymentMethod = paymentMethod;
        this.stock = stock;
        this.sales = sales;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Long completeSale(Long cashierId, PaymentDetails payment) {
        Long saleId = transactions.execute(status -> recordSale(cashierId, payment));
        carts.clear();
        return saleId;
    }

    // Read-write transaction, so the cart's discount and the stock are read on the primary.
    private Long recordSale(Long cashierId, PaymentDetails payment) {
        CartView cart = carts.view();
        if (cart.lines().isEmpty()) {
            throw new CartRuleException("The cart is empty");
        }
        PaymentResult paid = paymentMethod.pay(cart.total(), payment);
        for (CartLineView line : cart.lines()) {
            stock.deduct(line.id(), line.quantity());
        }
        Sale sale = new Sale(cashierId, cart.discountId(), Instant.now(clock), cart.subtotal(),
                cart.discountAmount(), cart.total(), cart.vatIncluded());
        cart.lines().forEach(line -> sale.addLine(line.id(), line.quantity(), line.unitPrice(), line.lineTotal()));
        sale.recordPayment(paid.method(), paid.amount(), paid.cashReceived(), paid.changeDue());
        return sales.save(sale).getId();
    }
}
