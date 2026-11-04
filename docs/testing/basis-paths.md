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
