# SmartMart Development Guidelines

These are the design and coding rules for SmartMart. They come from the CSE 3219 / CSE 3220 course material: Talha sir's design-pattern lectures (SEPM Lec 1 to 5), Nabil Faiyaz Sadi sir's lectures L1 to L15, and the CSE 3220 lab on REST and Spring Boot.

**Every team member must follow them.** Code, diagrams and documents that break a rule are fixed before they are merged into `main`. If a rule gets in the way of a real requirement, change the rule in this file first, in its own `docs(repo)` commit, and say why.

The words **must**, **must not** and **should** have their usual meaning: *must* is required, *should* is the default unless there is a written reason.

## Contents

1. [Process](#1-process)
2. [Technology stack](#2-technology-stack)
3. [Architecture](#3-architecture)
4. [Design principles](#4-design-principles)
5. [Design patterns](#5-design-patterns)
6. [REST API rules](#6-rest-api-rules)
7. [Security](#7-security)
8. [Data and persistence](#8-data-and-persistence)
9. [Testing](#9-testing)
10. [Documentation](#10-documentation)
11. [Git workflow](#11-git-workflow)
12. [Review checklist](#12-review-checklist)

## 1. Process

- We use the **Incremental model** with **Scrum-style sprints**. L1 lists Incremental as best for "well-defined, logically separated components (e.g., user module, payment, reporting)", which describes our five modules.
- Each increment delivers one module end to end, in the priority order from the README: POS, Inventory, Purchasing, User management, Reporting. User management (login and roles) is built in the first increment as a foundation, because every other module depends on it.
- Each sprint ends with a working demo, an updated diagram set, and passing tests.
- Stretch goals are started only after all five core modules work.

## 2. Technology stack

The stack matches the CSE 3220 lab (L3 slides and the instructor's demo repository), so lab instructions apply to us directly.

| Concern | Choice |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3 (Spring Web, Spring Data JPA, Spring Security, Validation) |
| Database | PostgreSQL |
| Migrations | Liquibase |
| Views | Thymeleaf |
| Build | Maven (with the Maven wrapper committed) |
| Boilerplate | Lombok (getters, constructors, builders only) |
| DTO mapping | ModelMapper or hand-written mappers |
| Containers | Docker and Docker Compose |
| Tests | JUnit 5, Mockito, Spring Boot Test |

- Projects are generated from [start.spring.io](https://start.spring.io/).
- Do not add a new library without team agreement. Record it in a `build(deps)` commit.

## 3. Architecture

### 3.1 Style: modular monolith, layered inside

- SmartMart is **one deployable Spring Boot application** with one logical PostgreSQL database, stored on **two database servers**: a primary that takes every write and a streaming replica that serves read-only queries (section 8.2). This is a **modular monolith** (L4).
- We do **not** build microservices. For one branch they add network calls, distributed transactions (L15) and CAP trade-offs (L11) with no benefit. Microservices, Saga and multi-branch sync are described only as future work in the report.
- Inside each module we use **N-tier layering** (L3): presentation → business logic → data access. Each layer calls only the layer below it.

### 3.2 Package layout

```
com.smartmart
├── pos/          cart, checkout, discounts, payments, invoices
├── inventory/    products, categories, stock batches, alerts
├── purchasing/   suppliers, purchase orders, receiving, returns
├── auth/         users, roles, login, security config
├── reports/      sales, purchase, inventory and profit reports
└── common/       shared config, errors, events, utilities
```

Each module has the same inner layout:

```
<module>/
├── web/          @Controller / @RestController classes, request and response DTOs
├── service/      business logic (interfaces and implementations)
├── domain/       JPA entities and value objects
└── repository/   Spring Data repositories
```

### 3.3 Layer rules

- Controllers **must** only parse input, call a service, and return a view or DTO. No business logic in controllers.
- Services hold all business rules and own transactions.
- Repositories are only called from services, never from controllers.
- Entities **must not** be returned from REST endpoints. Map them to DTOs.

### 3.4 Module boundary rules

- A module may call another module **only through that module's public service interface** (for example `InventoryService`), never through its repositories or entities' internals.
- When a module needs to react to something in another module (a sale reducing stock, stock falling below a threshold), use **events** (see Observer, section 5.2), not a direct call back into the first module.
- No circular dependencies between modules.

## 4. Design principles

### 4.1 SOLID (L7)

| Principle | Rule for SmartMart |
| --- | --- |
| **S**ingle Responsibility | One class has one reason to change. `CheckoutService` coordinates a sale; it does not calculate discounts, format receipts or write reports itself. |
| **O**pen–Closed | Adding a new discount type, payment method or report must not require editing existing classes. Add a new class instead (Strategy, Factory). |
| **L**iskov Substitution | Every implementation of an interface must honour its contract. A `DiscountStrategy` never returns a negative amount or a discount larger than the subtotal. |
| **I**nterface Segregation | Keep interfaces small and client-specific. Split read and write operations where clients differ (see Read-only Interface). |
| **D**ependency Inversion | Services depend on interfaces, not concrete classes. `CheckoutService` depends on `PaymentProcessor`, not on `CashPaymentProcessor`. |

### 4.2 Composition over inheritance (L8, Lec 1)

- Prefer **has-a** over **is-a**. Behaviour that varies (discounts, payments, receipt add-ons) is composed in as a field, not inherited.
- Use inheritance only for a true is-a relationship with a shared contract, and never more than two levels deep.
- Never subclass a class just to reuse one of its methods. Use Delegation (section 5.10).

### 4.3 Dependency Injection and Inversion of Control (L9)

- Spring owns object creation (IoC). Do not call `new` on services, repositories or strategies inside business code.
- Use **constructor injection** only. No field injection (`@Autowired` on fields) and no setter injection.
- Mark injected fields `private final`. With Lombok, use `@RequiredArgsConstructor`.

### 4.4 Other principles from the pattern lectures

- **Encapsulate what varies.** Identify the part of a feature that will change and put it behind an interface.
- **Program to an interface, not an implementation.**
- **Strive for loose coupling** between objects that interact.
- **Depend on abstractions** when creating objects (Factory).

## 5. Design patterns

These patterns are **required**. Each one is tied to a real SmartMart requirement. When you implement one:

1. Name the pattern in the class Javadoc.
2. Show it in the class diagram with its interfaces, concrete classes and relationships.
3. Write down **why** this pattern fits this requirement (Talha sir's rule for scenario answers). The design document must have one short paragraph per pattern: the requirement, the pattern, and the change it makes easy.

### 5.1 Strategy (Lec 1): discounts and payments

**Requirement:** new discount policies and payment methods are added without changing checkout code. (This is CT-1 Q2.)

```java
public interface DiscountStrategy {
    Money apply(Cart cart);          // returns the discount amount, never negative
    boolean isApplicable(Cart cart);
}
// PercentageDiscount, FlatAmountDiscount, BuyXGetYDiscount, MemberDiscount

public interface PaymentMethod {
    PaymentResult pay(Money amount, PaymentDetails details);
}
// CashPayment, CardPayment, MobileWalletPayment
```

- `CheckoutService` holds a list of `DiscountStrategy` beans and asks each one in turn.
- **Must not:** `if (type == "PERCENT") ... else if (type == "FLAT") ...` chains anywhere in POS code.

### 5.2 Observer (Lec 2): stock alerts

**Requirement:** when stock changes, several parts of the system react (low-stock alert, near-expiry alert, dashboard figures) without the stock code knowing about them.

- Use Spring application events: the inventory service publishes `StockChangedEvent`; `LowStockAlertListener`, `ExpiryAlertListener` and `DashboardListener` subscribe with `@EventListener`.
- The publisher must not hold references to concrete listeners.
- **Antipatterns to avoid (Lec 2):** a subject and observer holding references to each other; making observers subclasses of the subject.

### 5.3 Factory Method (Lec 3, Lec 4): creating payment processors and reports

**Requirement:** the caller asks for "a sales report" or "a card payment" without knowing which class is built.

- `ReportFactory.create(ReportType)` returns a `Report`; `PaymentMethodFactory.forType(PaymentType)` returns a `PaymentMethod`.
- With Spring, the factory may look implementations up from an injected `Map<String, Report>` instead of using `new`.
- The factory is the **only** place that switches on a type code.

### 5.4 Singleton (Lec 4): store settings

**Requirement:** one shared set of store settings (store name, VAT rate, currency, receipt header, low-stock default threshold).

- Implement as a Spring bean (`@Component` / `@ConfigurationProperties`), which is a singleton per application context.
- Do **not** hand-write `getInstance()` singletons. If one is ever unavoidable, it must be thread-safe (eager initialisation, holder class or enum). The lazy `if (instance == null)` version is not thread-safe (CT-1 Q1).
- Settings must be immutable after startup, or changed only through a synchronised service method.

### 5.5 Facade (Lec 4): checkout

**Requirement:** the POS screen completes a sale with one call, although a sale touches the cart, discounts, payment, stock and invoice.

- `CheckoutFacade.completeSale(cartId, paymentDetails)` (or `CheckoutService`) is the single entry point and the single transaction boundary for a sale.
- Controllers call the facade, not the subsystems one by one.

### 5.6 State (Lec 5): purchase orders and sales

**Requirement:** a purchase order behaves differently in each status, and illegal transitions are rejected.

- Purchase order states: `Draft → Submitted → PartiallyReceived → Received → Closed`, with `Cancelled` allowed from `Draft` or `Submitted`.
- Each state is a class implementing `PurchaseOrderState` (`submit()`, `receive()`, `cancel()`, `close()`). An illegal action throws `IllegalStateTransitionException`.
- The context (`PurchaseOrder`) holds the current-state reference. A state changes it through the context, never only through a local variable.
- The status is persisted as an enum column; the state object is rebuilt from it when loaded.
- **Must not:** large `switch (status)` blocks spread across services.

### 5.7 Decorator (Lec 3): receipts

**Requirement:** a receipt can include optional sections (VAT breakdown, loyalty points, promotion footer, duplicate-copy mark) in any combination.

- `ReceiptRenderer` is the component interface; `BasicReceipt` is the concrete component; `VatBreakdownDecorator`, `LoyaltyPointsDecorator`, `PromoFooterDecorator` wrap it.
- Adding a section means adding a decorator, not editing `BasicReceipt` (Open–Closed).

### 5.8 Adapter (Lec 4): external devices and services

**Requirement:** the system talks to things with interfaces we don't control (barcode scanner input, a mobile-wallet SDK, a PDF library).

- Define our own interface (`BarcodeReader`, `WalletGateway`, `PdfWriter`) and write one adapter class per external API.
- Business code depends only on our interface. External library types must not leak outside the adapter.

### 5.9 Memento (Lec 5): undo in the cart

**Requirement:** the cashier can undo the last cart change (void last item, undo quantity change) before payment.

- `Cart` (originator) creates `CartSnapshot` (memento); `CartHistory` (caretaker) keeps a bounded stack of snapshots.
- Snapshots are immutable. History is cleared when the sale completes.

### 5.10 Delegation (Lec 4)

- When a class needs a method another class already has, hold a reference and call it. For example, `Invoice` delegates totals to `Cart`; it does not extend `Cart`.
- **Antipatterns to avoid:** inheriting just to reuse a method; reaching through chains like `a.getB().getC().doThing()` (call a method on your direct neighbour instead).

### 5.11 Modelling patterns (Lec 5) for the domain model

| Pattern | Use in SmartMart |
| --- | --- |
| **Abstraction–Occurrence** | `Product` holds shared data (name, barcode, category, price). `StockBatch` holds each delivery's occurrence data (batch number, quantity, cost, expiry date). Never copy product name or price into every batch. |
| **General Hierarchy** | `Category` has an optional parent `Category`, forming a tree (Grocery → Rice → Miniket). |
| **Player–Role** | `User` (player) has one or more `Role`s (admin, manager, cashier, inventory staff). Roles are not subclasses of `User`, and a user's roles can change without changing class. |
| **Read-only Interface** | The reports module reads products and stock through read-only interfaces (`ProductView`, `StockView`). Only inventory services get the mutable interface. |

### 5.12 Optional and excluded patterns

- **Proxy** (handout, L13) may be used for a caching wrapper around product lookup if barcode lookups become slow.
- **Bridge** and **Flyweight** are not used; we have no requirement that needs them. Do not add a pattern just to show it.
- **Microservices, Saga, 2PC, pub-sub brokers (Kafka, RabbitMQ), RAG** are out of scope. Mention them only as future work.

## 6. REST API rules

From the CSE 3220 lab references (restapitutorial.com, the API design cheat sheet, and the Stack Overflow REST best-practices article).

- Base path: `/api/v1/`. A breaking change needs a new version.
- Resources are **plural nouns**, no verbs: `/api/v1/products`, `/api/v1/purchase-orders/{id}`.
- Nest only one level: `/api/v1/purchase-orders/{id}/items`. Deeper links return URIs instead.
- Use HTTP methods by meaning: `GET` read, `POST` create, `PUT` replace, `PATCH` partial update, `DELETE` remove. `GET`, `PUT` and `DELETE` must be idempotent.
- Actions that are not CRUD are modelled as resources: `POST /api/v1/sales` (complete a sale), `POST /api/v1/purchase-orders/{id}/receipts` (receive goods).
- Status codes: `200` OK, `201` Created with a `Location` header, `204` No Content, `400` validation error, `401` not logged in, `403` wrong role, `404` not found, `409` conflict (for example, illegal state transition or insufficient stock), `429` too many requests.
- Errors use one JSON shape everywhere, produced by a single `@RestControllerAdvice`:
  ```json
  { "status": 400, "error": "Bad Request", "message": "Quantity must be at least 1", "path": "/api/v1/sales" }
  ```
  Never return stack traces or SQL messages.
- Lists support `page`, `size` and `sort`, and filtering by query parameters (`?category=12&lowStock=true`).
- JSON uses camelCase. Dates and times use ISO 8601 (`2026-10-31T15:40:00+06:00`).

## 7. Security

From L6 (password hashing and JWT), L12 (rate limiting) and the lab's RBAC task.

- **Passwords** are hashed with **BCrypt (strength 12 or more)** through Spring Security's `PasswordEncoder`. Never store, log or email a plain password. Never write our own hashing.
- **Authentication:** Spring Security session login for Thymeleaf pages. If a separate JSON client is added, use **JWT** with a short access-token lifetime (15 minutes or less) and a refresh token stored server-side. The JWT payload carries the user id and roles only, never personal or secret data.
- **Authorisation (RBAC):** every endpoint and page is restricted by role in the security configuration or with `@PreAuthorize`. Default is deny.

  | Role | Allowed |
  | --- | --- |
  | Admin | User and role management, all settings |
  | Manager | Reports, stock and expiry views, approve purchase orders, discounts |
  | Cashier | POS checkout, own sales history |
  | Inventory staff | Products, categories, stock batches, suppliers, receiving |

- **Rate limiting:** limit failed logins per username and IP (token bucket, for example 5 attempts per minute), returning `429` with `Retry-After`.
- **Input validation** with Bean Validation (`@NotNull`, `@Positive`, `@Size`) on every request DTO.
- **Secrets** (database password, JWT key) come from environment variables. Commit `.env.example`, never `.env`.
- Use HTTPS in any deployed environment.

## 8. Data and persistence

### 8.1 Schema and data rules

- **PostgreSQL** is the only database engine. Every schema change is a **Liquibase changeset** committed with the code that needs it (`feat(db): ...`). Never edit the schema by hand and never change a changeset that has already been merged.
- Set `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate never changes the schema.
- **Money** uses `BigDecimal` in Java and `NUMERIC(12,2)` in the database. Never `double` or `float`.
- Quantities are integers or `NUMERIC` for weighed goods; never negative stock.
- A sale is one `@Transactional` service call: payment record, invoice, and stock deduction commit together or not at all.
- Stock is deducted from batches **first-expiry-first-out**.
- Every table has a primary key, `created_at` and `updated_at`.
- Naming: tables and columns in `snake_case`, tables plural (`products`, `stock_batches`).

### 8.2 Primary and replica

SmartMart runs **two PostgreSQL servers**. This is a team decision (see `docs/decisions.md`), not a stretch goal.

| Server | Role | Used for |
| --- | --- | --- |
| `db-primary` | Read-write | Every write, and every read inside a write transaction |
| `db-replica` | Read-only hot standby, kept up to date by **streaming replication** from the primary | Reports, dashboards, and list or search screens |

**Routing in the application**

- The application has two `DataSource`s: one for the primary and one for the replica. A `RoutingDataSource` (extending Spring's `AbstractRoutingDataSource`) picks one per transaction:
  - `@Transactional(readOnly = true)` goes to the **replica**.
  - Any other `@Transactional` goes to the **primary**.
- Wrap the routing data source in a `LazyConnectionDataSourceProxy`. Without it, the connection is taken before Spring marks the transaction read-only, and every query goes to the primary.
- The routing key comes from `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`. Services do not choose a database themselves, and no code outside `common/config` refers to "primary" or "replica".
- Liquibase runs **only against the primary**. The replica receives schema changes through replication.

**What must read from the primary**

The replica can be a little behind the primary (replication lag, usually well under a second, longer under load). So a read that must see a write that was just made **must not** be `readOnly = true`:

- Stock checks and stock deduction during checkout (they are inside the sale's write transaction anyway).
- Showing the invoice or receipt straight after a sale.
- Login and loading the current user's roles, so a role change applies at once.
- Purchase order status checks before a state transition (section 5.6).

Reports, dashboards, sales history and product search may read from the replica. A report can therefore miss the last second or so of sales. That is acceptable for SmartMart, and the report screen should show the time it was generated.

**Replication mode and its trade-off**

- Replication is **asynchronous** (`synchronous_commit` stays at its default, and `synchronous_standby_names` is empty). The primary confirms a commit without waiting for the replica.
- **Risk:** if the primary crashes, the replica may not yet have the last few seconds of committed writes. Those writes are lost when the replica is promoted. For a shop, that means the last sale or two may need to be re-entered from the paper or POS receipt.
- **Alternative we did not choose:** synchronous replication loses no committed write, but every sale waits for the replica, and if the replica is down, the primary stops accepting writes. For one branch with two servers, that turns a replica failure into a shop outage, so we accept the small loss window instead.

**Failover (manual)**

When the primary fails:

1. Confirm the primary is really down, so there are never two primaries accepting writes.
2. Promote the replica: `SELECT pg_promote();` (or `pg_ctl promote`). It becomes read-write.
3. Point both the primary and replica URLs of the application at the promoted server, and restart the application.
4. Re-enter any sales from the last few seconds that are missing (see the risk above).
5. Later, rebuild the old primary as the new replica with `pg_basebackup`. Never start it again as a primary.

Automatic failover tools (Patroni, repmgr, pgpool) are out of scope; mention them only as future work.

- If the replica is down, the application keeps working by setting the replica URL to the primary. This is a configuration change, not extra code.
- The primary keeps a replication slot (`replica_1`) so it holds WAL until the replica has received it. If the replica will be down for long, drop the slot (`SELECT pg_drop_replication_slot('replica_1');`) so the primary's disk does not fill, and rebuild the replica later.
- `compose.yaml` and `db/` start both servers locally with this setup.
- Both servers' connection settings come from environment variables (`DB_PRIMARY_URL`, `DB_REPLICA_URL`, and the credentials), per section 7.
- The replication password is a secret like any other. Commit `.env.example`, never `.env`.

## 9. Testing

From L14 (cyclomatic complexity and basis path testing) and the white-box / black-box references.

- **Unit tests** for every service class, with dependencies mocked through constructor injection (Mockito).
- **Integration tests** for each REST endpoint and repository with Spring Boot Test.
- **Cyclomatic complexity:** no method above **V(G) = 10**. If it is higher, split it or replace the branches with a pattern (Strategy, State).
- **Basis path testing** is required for the critical methods: discount calculation, checkout, stock deduction, and purchase order receiving. For each one, the test document shows the control-flow graph, V(G) = E − N + 2P, the independent paths, and one test case per path.
- **Black-box tests:** equivalence classes and boundary values for quantities, prices, discounts and thresholds (0, 1, maximum, just above maximum).
- Tests must pass before a commit is pushed to `main`. Never disable or delete a failing test to get green.

## 10. Documentation

- Diagrams live in `docs/uml/` and are updated in the same sprint as the code they describe: use case, class, activity, sequence and deployment diagrams.
- The class diagram shows every pattern in section 5 with its roles labelled.
- The deployment diagram shows the browser, the Spring Boot container, and the two PostgreSQL containers, primary and replica, with the replication link between them (Docker Compose). Add Nginx as a reverse proxy (L13) only if we deploy it.
- Public service interfaces have Javadoc that states the contract (inputs, outputs, exceptions).
- When a design decision is made, record it in `docs/decisions.md`: the date, the decision, and why.

## 11. Git workflow

- Follow [COMMIT.md](COMMIT.md): Conventional Commits, lowercase type, scope from its table, header of 72 characters or fewer.
- Enable the local hook after cloning: `git config core.hooksPath .githooks`.
- Work on `main`. Pull before you start, keep commits small and focused, and push only when the build and tests pass.
- Do not commit generated files, IDE folders, `target/`, or secrets.

## 12. Review checklist

Check every change against this list before pushing.

- [ ] Controllers contain no business logic; entities are not exposed through the API.
- [ ] Modules talk only through public service interfaces or events.
- [ ] New behaviour was added by a new class, not by editing an `if`/`switch` chain (Open–Closed).
- [ ] Dependencies are interfaces, injected through the constructor.
- [ ] No `new` of services, repositories or strategies in business code.
- [ ] Any pattern used is named in Javadoc, drawn in the class diagram, and justified in the design document.
- [ ] No method has cyclomatic complexity above 10.
- [ ] Endpoints follow the REST rules and are restricted by role.
- [ ] Passwords are hashed; no secrets in the code or the commit.
- [ ] Money uses `BigDecimal`; schema changes are Liquibase changesets.
- [ ] Read-only service methods that may run on the replica are `@Transactional(readOnly = true)`; reads that must see a just-made write are not.
- [ ] Tests are added or updated and pass.
- [ ] The commit message follows COMMIT.md.

## Sources

- Talha sir, SEPM Lec 1 to 5 (Strategy, Observer, Decorator, Factory Method, Singleton, Adapter, Facade, Delegation, State, Memento, Abstraction–Occurrence, General Hierarchy, Player–Role, Read-only Interface); Freeman and Robson, *Head First Design Patterns*.
- Nabil Faiyaz Sadi sir, CSE 3219 L1 (SDLC), L3 (architecture styles), L4 (monolith to microservices), L6 (password hashing and JWT), L7 (SOLID), L8 (composition over inheritance), L9 (DI and IoC), L12 (rate limiting), L13 (proxies and caching), L14 (cyclomatic complexity and basis path testing), L15 (distributed transactions).
- CSE 3220 L3 lab: REST API fundamentals and Spring Boot.
- CSE 3219 CT-1 question paper.
