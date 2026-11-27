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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * The checkout facade. The transaction is run through
 * {@link TransactionOperations} rather than {@code @Transactional}, so the
 * cart is cleared only once the commit has succeeded.
 * <p>
 * Each completed sale is counted for Prometheus (SCRUM-60): {@code smartmart.sales}
 * (sales), {@code smartmart.sales.revenue} (taka, VAT included) and
 * {@code smartmart.checkout.duration} (time to record a sale, failed ones
 * included, tagged by outcome).
 */
@Service
class CheckoutFacadeImpl implements CheckoutFacade {

    private final CartService carts;
    private final PaymentMethod paymentMethod;
    private final StockBatchService stock;
    private final SaleRepository sales;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Counter saleCount;
    private final Counter revenue;

    @Autowired
    CheckoutFacadeImpl(CartService carts, PaymentMethod paymentMethod, StockBatchService stock, SaleRepository sales,
                       TransactionOperations transactions, MeterRegistry meters) {
        this(carts, paymentMethod, stock, sales, transactions, Clock.systemUTC(), meters);
    }

    CheckoutFacadeImpl(CartService carts, PaymentMethod paymentMethod, StockBatchService stock, SaleRepository sales,
                       TransactionOperations transactions, Clock clock, MeterRegistry meters) {
        this.carts = carts;
        this.paymentMethod = paymentMethod;
        this.stock = stock;
        this.sales = sales;
        this.transactions = transactions;
        this.clock = clock;
        this.meters = meters;
        this.saleCount = Counter.builder("smartmart.sales").description("Completed sales").register(meters);
        this.revenue = Counter.builder("smartmart.sales.revenue").baseUnit("taka")
                .description("Value of completed sales, VAT included").register(meters);
    }

    @Override
    public Long completeSale(Long cashierId, PaymentDetails payment) {
        Timer.Sample timing = Timer.start(meters);
        String outcome = "refused";
        try {
            Sale sale = transactions.execute(status -> recordSale(cashierId, payment));
            outcome = "completed";
            saleCount.increment();
            revenue.increment(sale.getTotal().doubleValue());
            carts.clear();
            return sale.getId();
        } finally {
            timing.stop(Timer.builder("smartmart.checkout.duration").description("Time to record a sale")
                    .tag("outcome", outcome).publishPercentileHistogram().register(meters));
        }
    }

    // Read-write transaction, so the cart's discount and the stock are read on the primary.
    private Sale recordSale(Long cashierId, PaymentDetails payment) {
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
        sale.recordCustomer(carts.member().map(CustomerSummary::id).orElse(null));
        return sales.save(sale);
    }
}
