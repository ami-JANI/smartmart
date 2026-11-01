# Design Decisions

Design decisions about the SmartMart system, newest first. Each entry gives the date, the decision, and why (GUIDELINES.md section 10).

## 2026-11-01: Prices include VAT at one store-wide rate

**Decision.** Selling prices are entered and shown with VAT included. One VAT rate applies to the whole store and is kept in the store settings (GUIDELINES.md section 5.4). The invoice shows how much of the total is VAT. Products have no VAT rate of their own.

**Why.** Supermarkets in Bangladesh label shelf prices with VAT included, so the price the cashier charges matches the shelf. One rate keeps checkout and the invoice simple for a single branch.

**Alternatives considered.** A VAT rate per product, added on top at checkout: closer to the tax rules for mixed goods, but more fields to maintain and a total that differs from the shelf price. Leaving VAT out: rejected because the invoice should show it.

## 2026-11-01: Products are sold by piece or by weight

**Decision.** Each product has a sale unit: `PIECE` or `KG`. Piece quantities are whole numbers of at least 1. Weighed quantities are in kilograms with up to 3 decimal places and must be greater than 0. The price is per piece or per kilogram. The cashier types the weight; scale hardware is out of scope.

**Why.** A supermarket sells loose rice, vegetables and meat by weight. GUIDELINES.md section 8.1 already allows `NUMERIC` quantities for weighed goods.

**Alternatives considered.** Pieces only: simpler, but weighed goods would need fake pack sizes.

## 2026-11-01: Low-stock threshold per product with a store default

**Decision.** A product may have its own low-stock threshold. If it has none, the store-wide default from the store settings applies. A product is low on stock when its total quantity across all batches is at or below its threshold.

**Why.** Fast-moving goods need a higher threshold than slow ones, but setting one for every product is tedious, so a default covers the rest.

**Alternatives considered.** One global threshold (too coarse) or a required threshold on every product (too much data entry).

## 2026-11-01: Discounts and buy-X-get-Y promotions

**Decision.**

- Two kinds of price reduction exist in the first release:
  - **Discounts**: a percentage or a flat amount off the sale.
  - **Promotions**: buy X get Y free on one product, for example buy 2 get 1 free. The free units are the same product.
- Only managers create, change or end discounts and promotions. Cashiers cannot type their own amounts.
- A promotion applies automatically. For every X units paid, the next Y units of that product are free. The cashier scans every unit the customer takes, including the free ones, so stock stays correct.
- A product with an active promotion gets no other discount.
- The cashier may apply at most one discount to a sale. It reduces only the total of the lines that are not on promotion.
- A discount is never negative and never larger than the total it applies to.

**Why.** This is the team's rule: a promoted product's reward is the free item, not a second price cut. One discount per sale is simple to explain to customers and to test. Managers own discounts so cashiers cannot give away money (GUIDELINES.md section 7).

**Effect on the plan.** Buy-X-get-Y is new work for SCRUM-30, and nobody yet owns the manager screens for creating discounts and promotions. Both are noted in the SRS open issues.

**Alternatives considered.** Discounts stacking one after another, or the system choosing the best of several: rejected as harder to explain and test.

## 2026-10-31: Two database servers, primary and replica

**Decision.** SmartMart uses two PostgreSQL servers. The primary takes every write. A replica, kept up to date by asynchronous streaming replication, serves read-only queries: reports, dashboards, sales history and product search. The application routes each transaction with a Spring `AbstractRoutingDataSource`: `@Transactional(readOnly = true)` goes to the replica, everything else to the primary. If the primary fails, the replica is promoted by hand and the application is pointed at it. Details are in GUIDELINES.md section 8.2; `compose.yaml` runs the setup locally.

**Why.** The team requires more than one database. Two servers give:

- **Availability:** a copy of the data on a second server, which can take over if the primary crashes.
- **Read scaling:** heavy report queries do not slow down the POS checkout on the primary.
- **Course coverage:** it shows replication, consistency trade-offs (L11) and a richer deployment diagram.

**Trade-offs we accept.**

- **Replication lag.** The replica can be a little behind, so a report may miss the last second or so of sales. Reads that must see a write just made (checkout stock checks, the receipt after a sale, login and roles, purchase order state checks) stay on the primary.
- **Possible loss of the last few seconds of writes.** With asynchronous replication, the primary confirms a commit before the replica has it. If the primary crashes, those writes are lost when the replica is promoted, and the affected sales are re-entered from receipts.
- **More to run.** Two containers, a replication user, a replication slot to watch, and a manual failover procedure.

**Alternatives considered.**

- **One database (the previous plan).** Simpler, but rejected because the team requires more than one database.
- **Synchronous replication.** No committed write is lost, but every sale waits for the replica, and if the replica is down the primary stops accepting writes. Rejected for a single branch.
- **Automatic failover (Patroni, repmgr, pgpool).** Out of scope; future work.
- **Separate databases per module.** That is a step toward microservices, which GUIDELINES.md section 3.1 rules out.
