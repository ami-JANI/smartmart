# Design Decisions

Design decisions about the SmartMart system, newest first. Each entry gives the date, the decision, and why (GUIDELINES.md section 10).

## 2026-11-25: A dashboard for every role, and a demo login with every role

**Decision.**

- Every role opens `/dashboard`, but it shows only what that role's work covers. Cashiers see today's sales, the member count and this month's best sellers by quantity, without amounts. Inventory staff see stock alerts and the inventory status. Administrators see the number of user accounts. Managers see everything, as before. A user with several roles sees every part those roles cover.
- The demo data adds a `demo` login that holds all four roles, so the team can click through every page without logging out. It exists only with `LIQUIBASE_CONTEXTS=demo`.

**Why.** The team wanted the dashboard for everyone, but shop-wide revenue and member spending are manager information (GUIDELINES.md section 7), so each role gets its own view of the same page.

**Alternatives considered.** The full dashboard for every role: rejected because cashiers and stock staff would see revenue and member spending.

## 2026-11-23: Daily sales report

**Decision.**

- Managers get a daily report at `/reports/daily` (SCRUM-37, SCRUM-55). A day runs midnight to midnight in the store's time zone. It reads the replica and shows when it was generated.
- **Gross** is the sum of sale subtotals. **Net** is what customers paid: gross less discounts and promotion savings. Prices include VAT, so net includes the VAT, which is shown on its own line. Promotion savings are 0 until promotions are built.
- The per-product amounts are line totals before the sale-wide discount, so they add up to gross. A sale discount is not split across products.
- `ReportFactory` (GUIDELINES.md section 5.3) is not added yet, because there is only one report and nothing to choose between. It comes with the second report type.

**Why.** Emon's page (SCRUM-55) had clear acceptance criteria and all the data was already recorded, so the small service behind it was built with the page.

**Alternatives considered.** Net excluding VAT: rejected because shelf prices and the invoice total include VAT, so the manager can compare net with the cash taken.

## 2026-11-17: Members by phone and a manager dashboard

**Decision.**

- Customers can be registered as **members** by name and phone number (`customers` table, phone unique, stored as digits). The cashier can add a member to a sale by phone; the sale records `customer_id`, which is optional. Adding a member is not an undo step.
- Managers get a **dashboard** (`/dashboard`): today's sales, this month so far against the same days of last month, a 30-day chart, the five best sellers, the five best members, the products selling less than in the same days of last month, and the live stock status of every product.
- The dashboard reads the replica. The reports module uses only the pos `SalesStatistics` service and the inventory `ProductService` and `AlertService`, never their repositories (GUIDELINES.md section 3.4). The chart is drawn as inline SVG, with no chart library.

**Why.** The team asked for a dashboard that shows monthly sales, best products, best customers, sales drops and stock in one place. "Best customers" needs sales to know their customer, so a minimal membership by phone was taken in early, although the README lists loyalty as a stretch goal. Comparing month-to-date with the same days of last month keeps a half-finished month from looking like a drop.

**Not included.** Loyalty points, member discounts and editing or removing members are not built.

## 2026-11-17: First discounts and cash sale without promotions or manager screens

**Decision.**

- SCRUM-30 and SCRUM-31 are built in Sprint 3 for percentage and flat discounts and cash payment only. Buy-X-get-Y promotions (FR-POS-7) stay on hold, so promotion savings are 0 and the picked discount applies to the whole subtotal.
- Until a story covers manager screens for discounts (SRS Q1), discounts are added as Liquibase changesets (`seed-discounts.yaml`). A discount is valid from its start date to its end date, both included.
- A completed sale is stored as `sales`, `sale_lines` and `payments`. The sale row is also the invoice; its number is formed from the id (`INV-000042`). Lines keep the price the product was sold at.
- `CheckoutFacade.completeSale` runs the sale in one read-write transaction and clears the cart only after the commit, so a failed sale leaves the cart as it was (FR-POS-10).

**Why.** The Sprint 3 goal is a cash sale end to end. Percentage and flat discounts were already built as strategies, and the SRS suggested a Liquibase data file for v1 managers. Promotions need the manager screens and would raise SCRUM-30 to about 5 points (SRS Q3), so they wait.

**Alternatives considered.** A separate `invoices` table: rejected for v1 because it would copy every figure the sale already holds. Clearing the cart inside the transaction: rejected because a failed commit would lose the cart.

## 2026-11-09: Near-expiry alerts default to 7 days before batch expiry

**Decision.** Batches that expire within a configurable window (store setting `near-expiry-days-default`, default 7 days) trigger a `NEAR_EXPIRY` alert. Already-expired batches are excluded from sales (FR-POS-11) and flagged on the batch list, but near-expiry alerts warn inventory staff and managers before batches expire so goods can be discounted, promoted or returned.

**Why.** Answers SRS Q2. Fast-moving fresh products and packaged foods in a single-branch supermarket need enough lead time (about a week) for staff to take action before the product reaches its expiry date.

**Alternatives considered.** Setting near-expiry days per category or per product: rejected for v1 as overly complex for initial inventory management (simplicity first, GUIDELINES.md section 2).

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
