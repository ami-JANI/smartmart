# Basis Path Tests

GUIDELINES.md section 9 requires basis path testing for the critical methods: discount calculation, checkout, stock deduction and purchase order receiving. For each method this document gives the control-flow graph, the cyclomatic complexity V(G) = E − N + 2P, the independent paths, and the test that covers each path.

## Stock deduction: `StockBatchServiceImpl.deduct(productId, quantity)`

Takes a quantity of a product out of its batches, first-expiry-first-out (FR-POS-11), or refuses if the unexpired batches hold too little (FR-POS-12).

```java
Product product = products.findById(productId).orElseThrow(...);         // 1, 2
List<StockBatch> sellable = batches.lockSellable(productId, today);       // 3
BigDecimal available = sum of sellable quantities;                        // 3
if (available.compareTo(quantity) < 0) {                                  // 4
    throw new InsufficientStockException(product.getName());              // 5
}
BigDecimal remaining = quantity;                                          // 6
for (StockBatch batch : sellable) {                                       // 7
    if (remaining.signum() == 0) {                                        // 8
        break;
    }
    remaining = remaining.subtract(batch.take(remaining));                // 9
}
                                                                          // 10 (exit)
```

### Control-flow graph

| Node | Statement |
| --- | --- |
| 1 | Look up the product |
| 2 | Product not found: throw `ProductNotFoundException` |
| 3 | Lock the sellable batches and add up their quantities |
| 4 | Decision: enough stock? |
| 5 | Throw `InsufficientStockException` |
| 6 | `remaining = quantity` |
| 7 | Decision: another batch in the loop? |
| 8 | Decision: nothing left to take? |
| 9 | Take from the batch |
| 10 | Exit |

Edges: 1→2, 1→3, 2→10, 3→4, 4→5, 4→6, 5→10, 6→7, 7→8, 7→10, 8→10 (break), 8→9, 9→7.

E = 13, N = 10, P = 1, so **V(G) = 13 − 10 + 2 = 5**. This matches the 4 decisions (nodes 1, 4, 7, 8) plus 1.

### Independent paths and tests

Tests are in `src/test/java/com/smartmart/inventory/service/StockDeductionTest.java`.

| Path | Nodes | Input | Expected | Test |
| --- | --- | --- | --- | --- |
| 1 | 1, 2, 10 | Unknown product | `ProductNotFoundException` | `path1MissingProductIsNotFound` |
| 2 | 1, 3, 4, 5, 10 | Batches of 2 and 1.5, deduct 3.501 | "Not enough stock for Miniket rice"; batches unchanged | `path2NotEnoughStockIsRefusedAndNothingIsTaken` |
| 3 | 1, 3, 4, 6, 7, 10 | No batches, deduct 0 | Nothing taken | `path3NoBatchesAndNothingWantedTakesNothing` |
| 4 | 1, 3, 4, 6, 7, 8, 9, 7, 10 | One batch of 3, deduct 3 | Batch left at 0 | `path4OneBatchWithExactlyEnoughIsEmptied` |
| 5 | 1, 3, 4, 6, 7, 8, 9, 7, 8, 10 | Batches of 5 and 4, deduct 2 | 3 and 4 left (break before the second batch) | `path5SoonestBatchCoversItAndLaterBatchesAreUntouched` |

One more test, `shortfallInTheSoonestBatchComesFromTheNext`, takes 3.25 from batches of 2 and 4 (the loop body runs twice). `StockDeductionTests` runs the method against the databases to check what the batch query selects: expired and empty batches are skipped, and batches without an expiry date come last.

## Discount calculation: `PercentageDiscount.discountOn` and `FlatAmountDiscount.discountOn`

Each strategy works out the amount off the total it applies to (FR-POS-8). Both have the same shape:

```java
if (eligibleTotal.signum() <= 0) {                 // 1
    return BigDecimal.ZERO.setScale(2);            // 2
}
return amount worked out from eligibleTotal;       // 3  (percent × total ÷ 100, or min(flat, total))
                                                   // 4 (exit)
```

Edges: 1→2, 1→3, 2→4, 3→4. E = 4, N = 4, P = 1, so **V(G) = 4 − 4 + 2 = 2** for each method.

Tests are in `src/test/java/com/smartmart/pos/domain/DiscountStrategyTest.java`.

| Method | Path | Input | Expected | Test |
| --- | --- | --- | --- | --- |
| Percentage | 1, 2, 4 | 10% of 0.00 | 0.00 | `percentageOfNothingIsZero` |
| Percentage | 1, 3, 4 | 10% of 500.00 | 50.00 | `tenPercentOfFiveHundredIsFifty` |
| Flat | 1, 2, 4 | 100.00 off 0.00 | 0.00 | `flatAmountOnNothingIsZero` |
| Flat | 1, 3, 4 | 100.00 off 60.00 | 60.00 (capped) | `flatAmountAboveTheTotalIsCappedAtTheTotal` |

The boundary tests for 0, 1, 100 and 100.01 percent, and for a flat amount below, at and above the total, are in the same class. `CartServiceImplTest` checks that the cart applies the picked discount, that a second pick replaces the first, and that undo reverses a pick.

## Checkout: `CheckoutFacadeImpl.recordSale(cashierId, payment)`

Runs inside the sale's single transaction, called from `completeSale` (FR-POS-9 to FR-POS-12).

```java
CartView cart = carts.view();                                             // 1
if (cart.lines().isEmpty()) {                                             // 2
    throw new CartRuleException("The cart is empty");                     // 3
}
PaymentResult paid = paymentMethod.pay(cart.total(), payment);            // 4
for (CartLineView line : cart.lines()) {                                  // 5
    stock.deduct(line.id(), line.quantity());                             // 6
}
Sale sale = new Sale(...); add the lines; record the payment;             // 7
return sales.save(sale).getId();                                          // 7
                                                                          // 8 (exit)
```

### Control-flow graph

| Node | Statement |
| --- | --- |
| 1 | Read the cart with its discount and totals |
| 2 | Decision: is the cart empty? |
| 3 | Throw `CartRuleException` |
| 4 | Take the payment (a refusal throws `PaymentRefusedException`) |
| 5 | Decision: another line in the loop? |
| 6 | Deduct the line from stock (a shortfall throws `InsufficientStockException`) |
| 7 | Build and save the sale with its lines and payment |
| 8 | Exit |

Edges: 1→2, 2→3, 2→4, 3→8, 4→5, 5→6, 6→5, 5→7, 7→8.

E = 9, N = 8, P = 1, so **V(G) = 9 − 8 + 2 = 3**. This matches the 2 decisions (nodes 2 and 5) plus 1.

### Independent paths and tests

Tests are in `src/test/java/com/smartmart/pos/service/CheckoutFacadeImplTest.java`.

| Path | Nodes | Input | Expected | Test |
| --- | --- | --- | --- | --- |
| 1 | 1, 2, 3, 8 | Empty cart | "The cart is empty"; no stock or sale touched; cart kept | `emptyCartIsRefusedAndNothingHappens` |
| 2 | 1, 2, 4, 5, 6, 5, 7, 8 | One line of 500.00, 10% discount, cash 500 | Sale saved with total 450.00 and change 50.00; cart cleared | `oneLineSaleIsSavedWithItsPaymentAndTheCartIsCleared` |
| 3 | 1, 2, 4, 5, 6, 5, 6, 5, 7, 8 | Two lines, one weighed (1.250 kg) | Each line deducted and saved at its price | `everyLineIsDeductedAndSavedAtItsPrice` |

A non-empty cart always enters the loop, so the path 1, 2, 4, 5, 7, 8 (no lines) cannot happen. The exceptions thrown inside nodes 4 and 6 have their own tests: `cashBelowTheTotalIsRefusedBeforeStockIsTouched` and `notEnoughStockRefusesTheSaleAndKeepsTheCart`. `CheckoutPageTests` runs real sales on the database and checks that a refused sale saves nothing and leaves stock and the cart as they were.
