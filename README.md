<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=spring-boot&logoColor=white" alt="Spring Boot 3.5" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL 16" />
  <img src="https://img.shields.io/badge/Thymeleaf-3-005F0F?logo=thymeleaf&logoColor=white" alt="Thymeleaf" />
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white" alt="Docker Compose" />
  <img src="https://img.shields.io/badge/License-Unlicensed-lightgrey" alt="License" />
</p>

# 🛒 SmartMart — Integrated Super Shop Management System

> **A full-featured, single-branch supermarket management system** covering point of sale, inventory tracking, purchasing, user administration, reporting dashboards, and real-time monitoring — built with Spring Boot, PostgreSQL streaming replication, and a clean modular-monolith architecture.

SmartMart is a three-person academic project for **CSE 3219: Software Engineering and Project Management** and **CSE 3220: SEPM Laboratory** at [Khulna University of Engineering & Technology (KUET)](https://www.kuet.ac.bd). It demonstrates industry-grade software engineering practices — design patterns, SOLID principles, database replication, CI/CD, and observability — while solving real problems faced by supermarket chains in Bangladesh.

---

## 📑 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Technology Stack](#-technology-stack)
- [Prerequisites](#-prerequisites)
- [Quick Start](#-quick-start)
- [Demo Data & Credentials](#-demo-data--credentials)
- [Configuration Reference](#-configuration-reference)
- [Monitoring & Observability](#-monitoring--observability)
- [Database Replication](#-database-replication)
- [Testing](#-testing)
- [Project Structure](#-project-structure)
- [Design Patterns](#-design-patterns)
- [CI/CD Pipeline](#-cicd-pipeline)
- [Contributing](#-contributing)
- [Course Deliverables](#-course-deliverables)
- [Team](#-team)
- [License](#-license)

---

## ✨ Features

### Point of Sale (POS)
- **Barcode-based cart** — scan or type a 13-digit barcode to add products; weighed goods (KG) prompt for weight input
- **Undo system** — Memento-pattern cart history lets cashiers undo the last action before payment
- **Discounts** — Strategy-pattern percentage and flat-amount discounts, managed by store managers via Liquibase seed data
- **Promotions** — Buy-X-get-Y-free offers that apply automatically at checkout
- **Cash checkout** — Facade-pattern single-transaction sale: payment → invoice → stock deduction (FEFO) atomically
- **Invoice generation** — Auto-numbered invoices (`INV-000042`) with VAT breakdown, printable from the browser
- **Member lookup** — Register customers by phone; attach members to sales for analytics

### Inventory Management
- **Category tree** — Hierarchical categories with parent–child relationships (General Hierarchy pattern)
- **Product catalogue** — Full CRUD with barcode, sale unit (PIECE/KG), price, and per-product low-stock thresholds
- **Stock batches** — Track each delivery separately: batch number, quantity, unit cost, expiry date
- **Automated alerts** — Observer-pattern event listeners raise `LOW_STOCK` and `NEAR_EXPIRY` alerts automatically when stock changes
- **FEFO stock deduction** — First-Expiry-First-Out ensures oldest batches sell first; expired batches are blocked from sale

### Purchasing & Suppliers
- **Supplier management** — Full CRUD for supplier records with contact details
- **Purchase orders** — State-pattern lifecycle: `Draft → Submitted → Partially Received → Received → Closed` (or `Cancelled`)
- **Goods receiving** — Record deliveries line-by-line with batch numbers and expiry dates; stock batches are created automatically
- **Pessimistic locking** — `SELECT ... FOR UPDATE` prevents concurrent over-receiving of the same order

### User Management & Security
- **Role-based access control (RBAC)** — Four roles: `ADMIN`, `MANAGER`, `CASHIER`, `INVENTORY_STAFF`
- **Role hierarchy** — `ADMIN` implies all lower roles; can access every page and perform every action
- **Admin seeder** — First administrator created at startup from environment variables
- **BCrypt hashing** — Passwords hashed at strength 12; never stored, logged, or transmitted in plaintext
- **Login rate limiting** — Token-bucket limiter: 5 failed attempts per username/IP per minute, then HTTP 429 with `Retry-After`
- **Fresh roles** — Roles reloaded on every request; a role change takes effect immediately without re-login
- **Custom error pages** — Branded pages for 401, 403, 404, 429, and 5xx errors

### Reporting & Dashboard
- **Manager dashboard** — Today's sales vs. last month, 30-day trend chart (inline SVG, no JS charting library), top 5 best sellers, top 5 members, sales-drop detection, live stock status
- **Role-specific dashboards** — Each role sees only its relevant metrics: cashiers see sales counts, inventory staff see alerts, admins see user counts
- **Daily sales report** — Printable per-day report with gross, net, VAT, and per-product breakdowns
- **Replica-powered reads** — Reports and dashboards run on the read replica to keep the POS checkout snappy

### Monitoring & Observability
- **Spring Boot Actuator** — Health, liveness, readiness probes with custom primary/replica database checks and replication lag reporting
- **Prometheus metrics** — Custom business gauges: `smartmart.sales`, `smartmart.sales.revenue`, `smartmart.checkout.duration`, `smartmart.stock.alerts.open`
- **Grafana dashboards** — Two pre-provisioned dashboards: "Sales and Stock" (business) and "JVM and System" (ops)
- **Secured scraping** — Dedicated HTTP Basic security chain for Prometheus; revenue metrics stay private

---

## 🏗 Architecture

SmartMart is a **modular monolith** with **N-tier layering** inside each module. It uses two PostgreSQL servers: a primary for writes and a streaming replica for read-only queries.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Browser (Desktop)                        │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP
┌──────────────────────────────▼──────────────────────────────────┐
│                   Spring Boot Application (:8080)                │
│  ┌─────────┐ ┌───────────┐ ┌────────────┐ ┌──────┐ ┌─────────┐ │
│  │   POS   │ │ Inventory │ │ Purchasing │ │ Auth │ │ Reports │ │
│  │ ┌─────┐ │ │ ┌───────┐ │ │ ┌────────┐ │ │      │ │         │ │
│  │ │ web │ │ │ │  web  │ │ │ │  web   │ │ │      │ │         │ │
│  │ │ svc │ │ │ │  svc  │ │ │ │  svc   │ │ │      │ │         │ │
│  │ │ dom │ │ │ │  dom  │ │ │ │  dom   │ │ │      │ │         │ │
│  │ │repo │ │ │ │ repo  │ │ │ │ repo   │ │ │      │ │         │ │
│  │ └─────┘ │ │ └───────┘ │ │ └────────┘ │ │      │ │         │ │
│  └─────────┘ └───────────┘ └────────────┘ └──────┘ └─────────┘ │
│                         common/config                            │
│              RoutingDataSource · StoreSettings · Health          │
└────────────┬───────────────────────────────┬────────────────────┘
             │ read-write                    │ read-only
   ┌─────────▼─────────┐          ┌──────────▼──────────┐
   │   PostgreSQL 16   │ ──WAL──▶ │   PostgreSQL 16    │
   │   db-primary      │ streaming│   db-replica        │
   │   :5432           │ repl.    │   :5433             │
   └───────────────────┘          └─────────────────────┘

   ┌───────────────────┐          ┌─────────────────────┐
   │ Prometheus :9090  │ scrape   │   Grafana :3000     │
   │                   │ ───────▶ │   2 dashboards      │
   └───────────────────┘          └─────────────────────┘
```

### Module Boundaries

- Modules communicate **only through public service interfaces**, never through repositories or entity internals
- Cross-module reactions use **Spring application events** (Observer pattern), not direct callbacks
- No circular dependencies between modules

---

## 🔧 Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.5 (Web, Data JPA, Security, Validation, Actuator) |
| **Template Engine** | Thymeleaf with layout dialect |
| **Database** | PostgreSQL 16 (primary + streaming replica) |
| **Migrations** | Liquibase (changelog-driven, `validate` mode) |
| **Metrics** | Micrometer + Prometheus Registry |
| **Monitoring** | Prometheus 2.55 + Grafana 11.3 |
| **Build** | Maven 3 (wrapper included) |
| **Boilerplate** | Lombok (getters, constructors, builders only) |
| **Containers** | Docker + Docker Compose |
| **CI/CD** | GitHub Actions |
| **Testing** | JUnit 5, Mockito, Spring Boot Test, Spring Security Test |

---

## 📋 Prerequisites

| Tool | Version | Notes |
| :--- | :--- | :--- |
| **JDK** | 17 or newer | For running on host; Docker uses Eclipse Temurin |
| **Docker Desktop** | Latest | Must be running before `docker compose` |
| **Git** | 2.x | For cloning and hooks |

> Maven is **not** required. The project includes the Maven wrapper (`./mvnw` / `mvnw.cmd`).

---

## 🚀 Quick Start

### 1. Clone and configure

```bash
git clone https://github.com/ami-JANI/smartmart.git
cd smartmart

# Set up Git hooks and commit template
git config core.hooksPath .githooks
git config commit.template .gitmessage

# Create your local environment file
cp .env.example .env
```

Edit `.env` and set real passwords. **Never commit `.env`.**

### 2a. Run everything in Docker (recommended)

```bash
docker compose up -d --build
```

This starts the databases, the application, Prometheus, and Grafana.  
Open **http://localhost:8080** and log in.

### 2b. Run the application on your machine (for development)

```bash
# Start only the databases and monitoring
docker compose up -d db-primary db-replica prometheus grafana

# Run the application with hot-reload on code changes
./mvnw spring-boot:run
```

> If port 8080 is in use (e.g., Oracle TNSLSNR on Windows):
> ```bash
> ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
> ```

### 3. Stop

```bash
docker compose down        # Keeps data
docker compose down -v     # Deletes all data (fresh start)
```

---

## 🔐 Demo Data & Credentials

Add `LIQUIBASE_CONTEXTS=demo` to your `.env` to load a sample shop with 12 products, stock batches, suppliers, alerts, six members, and two months of sales history.

### Login Credentials

| Username | Password | Role | Lands on |
| :--- | :--- | :--- | :--- |
| `admin` | *(your `ADMIN_PASSWORD`)* | Administrator | User management |
| `manager` | `smartmart-demo` | Manager | Dashboard with analytics |
| `cashier` | `smartmart-demo` | Cashier | POS: scan barcodes, checkout |
| `stock` | `smartmart-demo` | Inventory Staff | Products, suppliers, alerts |
| `demo` | `smartmart-demo` | **All roles** | Every page in one login |

### Demo Barcodes

| Barcode | Product | Type |
| :--- | :--- | :--- |
| `2000000000103` | Bath soap 100g | Piece (low stock alert active) |
| `2000000000011` | Miniket rice | By weight (KG) |
| `2000000000066` | Fresh milk 1L | Piece (near-expiry alert active) |

### Demo Members

Phones `01711000101` through `01711000106` — use at POS to attach a member to a sale.

> **To reset:** `docker compose down -v && docker compose up -d --build`

---

## ⚙ Configuration Reference

All configuration is via environment variables in `.env`:

| Variable | Purpose | Default |
| :--- | :--- | :--- |
| `DB_NAME` | Database name | `smartmart` |
| `DB_USER` | Database user | `smartmart` |
| `DB_PASSWORD` | Database password | *(required)* |
| `REPLICATION_USER` | Streaming replication user | `replicator` |
| `REPLICATION_PASSWORD` | Replication password | *(required)* |
| `DB_PRIMARY_URL` | JDBC URL for primary | `jdbc:postgresql://localhost:5432/smartmart` |
| `DB_REPLICA_URL` | JDBC URL for replica | `jdbc:postgresql://localhost:5433/smartmart` |
| `ADMIN_USERNAME` | First admin account username | *(blank = no admin created)* |
| `ADMIN_PASSWORD` | First admin account password | *(blank = no admin created)* |
| `LIQUIBASE_CONTEXTS` | Set to `demo` for sample data | `none` |
| `METRICS_PASSWORD` | Prometheus scraper password | *(blank = endpoint closed)* |
| `GRAFANA_ADMIN_PASSWORD` | Grafana admin password | `change-me-grafana` |
| `GRAFANA_URL` | URL for the Monitoring link | `http://localhost:3000` |

---

## 📊 Monitoring & Observability

| Service | URL | Access |
| :--- | :--- | :--- |
| **Health** | http://localhost:8080/actuator/health | Public (UP/DOWN); admins see database details |
| **Liveness** | http://localhost:8080/actuator/health/liveness | Public |
| **Readiness** | http://localhost:8080/actuator/health/readiness | Public (checks both databases) |
| **Metrics** | http://localhost:8080/actuator/metrics | Administrators only |
| **Prometheus** | http://localhost:9090 | Scrapes `/actuator/prometheus` with `METRICS_PASSWORD` |
| **Grafana** | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` |

### Custom Business Metrics

| Metric | Type | Description |
| :--- | :--- | :--- |
| `smartmart.sales` | Counter | Total completed sale transactions |
| `smartmart.sales.revenue` | Counter | Cumulative revenue in BDT (VAT included) |
| `smartmart.checkout.duration` | Timer | Checkout execution time, tagged `completed`/`refused` |
| `smartmart.stock.alerts.open` | Gauge | Currently active low-stock and near-expiry alerts |

### Grafana Dashboards

- **SmartMart: Sales and Stock** — Sales rate, revenue, checkout latency, open alerts
- **SmartMart: JVM and System** — CPU, memory, GC, HTTP traffic, database connection pools

Both dashboards are provisioned from files in `monitoring/grafana/` — changes are version-controlled, not made in the Grafana UI.

---

## 🗄 Database Replication

SmartMart uses **asynchronous streaming replication** between two PostgreSQL 16 servers:

| Server | Port | Role | Used for |
| :--- | :--- | :--- | :--- |
| `db-primary` | 5432 | Read-write | All writes, reads that must see latest data |
| `db-replica` | 5433 | Read-only hot standby | Reports, dashboards, product search |

### How routing works

- `@Transactional(readOnly = true)` → routes to **replica**
- `@Transactional` (default) → routes to **primary**
- A `RoutingDataSource` with `LazyConnectionDataSourceProxy` handles this transparently
- No application code outside `common/config` references "primary" or "replica"

### Trade-offs

- **Replication lag:** The replica may be a fraction of a second behind. Reports show a generated timestamp.
- **Failover:** Manual only. Promote the replica with `SELECT pg_promote();`, repoint the app, rebuild the old primary.
- **Data loss window:** Asynchronous replication means the last 1–2 seconds of commits may be lost on primary crash.

> See [docs/decisions.md](docs/decisions.md) for the full rationale.

---

## 🧪 Testing

```bash
# Start the databases (both required)
docker compose up -d db-primary db-replica

# Run all tests
./mvnw verify
```

| Metric | Value |
| :--- | :--- |
| **Test count** | 373 |
| **Test files** | 48 |
| **Framework** | JUnit 5 + Mockito + Spring Boot Test |
| **Coverage areas** | Service unit tests, controller integration tests, security/auth tests, database routing tests |

### Testing approach

- **Unit tests** for every service, with mocked dependencies (Mockito)
- **Integration tests** for controllers using `MockMvc` with `@SpringBootTest` and both real databases
- **Security tests** verifying role restrictions, login flow, rate limiting, and CSRF
- **Replica routing tests** confirming read-only transactions reach the replica
- **Basis path testing** for critical methods: checkout, discount calculation, stock deduction, purchase order state transitions

> Tests clean up after themselves. GitHub Actions runs the same build on every push to `main`.

---

## 📁 Project Structure

```
smartmart/
├── .github/
│   ├── workflows/
│   │   ├── build.yml              # CI: test on every push to main
│   │   └── commit-messages.yml    # Conventional Commits check
│   └── scripts/
├── .githooks/                     # Local commit-msg validation hook
├── db/
│   ├── primary/                   # Replication init script
│   └── replica/                   # Replica start script
├── docs/
│   ├── decisions.md               # Architecture Decision Records
│   ├── srs/srs-v1.md             # Software Requirements Specification
│   ├── testing/basis-paths.md     # Basis path test documentation
│   ├── plan/                      # WBS, estimates, schedule, risks
│   └── uml/                       # PlantUML: use case, class, ERD, sequence, activity
├── monitoring/
│   ├── grafana/
│   │   ├── dashboards/            # JSON dashboard definitions
│   │   └── provisioning/          # Datasource and dashboard provisioning
│   └── prometheus/
│       └── prometheus.yml         # Scrape configuration
├── src/main/java/com/smartmart/
│   ├── SmartMartApplication.java
│   ├── auth/                      # Users, roles, login, security
│   │   ├── domain/                #   User, Role, RoleName entities
│   │   ├── repository/            #   UserRepository, RoleRepository
│   │   ├── service/               #   UserService, AdminAccountSeeder
│   │   └── web/                   #   SecurityConfig, LoginController, UserController
│   ├── common/                    # Shared infrastructure
│   │   ├── config/                #   DataSourceConfig, RoutingDataSource, StoreSettings
│   │   └── error/                 #   ApiExceptionHandler, ApiError
│   ├── inventory/                 # Products, categories, stock, alerts
│   │   ├── domain/                #   Product, Category, StockBatch, StockAlert
│   │   ├── repository/            #   ProductRepository, StockBatchRepository, etc.
│   │   ├── service/               #   ProductService, StockBatchService, AlertService
│   │   └── web/                   #   ProductController, CategoryController, AlertController
│   ├── pos/                       # Cart, checkout, discounts, invoices
│   │   ├── domain/                #   Cart, CartLine, Sale, Discount, Payment (Strategy, Memento)
│   │   ├── repository/            #   SaleRepository, DiscountRepository
│   │   ├── service/               #   CartService, CheckoutFacade, InvoiceService
│   │   └── web/                   #   CartController, CheckoutController, InvoiceController
│   ├── purchasing/                # Suppliers, purchase orders
│   │   ├── domain/                #   PurchaseOrder, Supplier, State pattern classes
│   │   ├── repository/            #   PurchaseOrderRepository, SupplierRepository
│   │   ├── service/               #   PurchaseOrderService, SupplierService
│   │   └── web/                   #   PurchaseOrderController, SupplierController
│   └── reports/                   # Dashboard, daily sales reports
│       ├── service/               #   DashboardService, DailySalesReportService
│       └── web/                   #   DashboardController, DailySalesReportController
├── src/main/resources/
│   ├── application.yml            # Spring Boot configuration
│   ├── db/changelog/              # Liquibase migrations
│   ├── static/                    # CSS (app.css), JS (pos.js)
│   └── templates/                 # Thymeleaf templates (32 pages)
├── src/test/java/                 # 48 test classes, 373 tests
├── compose.yaml                   # Docker Compose: app, 2×DB, Prometheus, Grafana
├── Dockerfile                     # Multi-stage build (Temurin 17)
├── pom.xml                        # Maven dependencies
├── GUIDELINES.md                  # Development rules & review checklist
├── COMMIT.md                      # Conventional Commits specification
└── .env.example                   # Environment variable template
```

**Source statistics:** 185 source files · 48 test files · 373 tests · 32 Thymeleaf templates · 163+ commits

---

## 🎨 Design Patterns

Every pattern is tied to a real requirement, documented in Javadoc, shown in the class diagram, and justified in [docs/decisions.md](docs/decisions.md).

| Pattern | Where | Purpose |
| :--- | :--- | :--- |
| **Strategy** | `DiscountStrategy` → `PercentageDiscount`, `FlatAmountDiscount` | Add discount types without editing checkout |
| **Observer** | `StockChangedEvent` → `LowStockAlertListener`, `ExpiryAlertListener` | Decouple stock changes from alert logic |
| **Facade** | `CheckoutFacade.completeSale()` | Single entry point for the multi-step checkout transaction |
| **State** | `PurchaseOrderState` → `DraftState`, `SubmittedState`, `ReceivedState`… | Enforce legal state transitions; reject illegal ones |
| **Memento** | `CartSnapshot` + `CartHistory` | Undo the last cart change before payment |
| **Singleton** | `StoreSettings` (Spring `@Component`) | One shared set of store-wide configuration |
| **Factory Method** | `PaymentMethodFactory`, `ReportFactory` (planned) | Create objects without exposing concrete classes |
| **Decorator** | `ReceiptRenderer` chain (planned) | Compose optional invoice sections |
| **Adapter** | `BarcodeReader`, `PdfWriter` interfaces | Isolate external library types from business code |
| **Delegation** | `Invoice` delegates totals to `Cart` | Reuse without inheritance |
| **Abstraction–Occurrence** | `Product` ↔ `StockBatch` | Shared data vs. per-delivery data |
| **General Hierarchy** | `Category` tree (self-referencing parent) | Unlimited nesting of product categories |
| **Player–Role** | `User` ↔ `Role` (many-to-many) | Roles are not subclasses; they can change at runtime |

---

## 🔄 CI/CD Pipeline

GitHub Actions runs on every push to `main`:

| Workflow | File | What it does |
| :--- | :--- | :--- |
| **Build** | `.github/workflows/build.yml` | Starts both PostgreSQL containers, runs `./mvnw verify` (compile + all 373 tests) |
| **Commit Messages** | `.github/workflows/commit-messages.yml` | Validates every commit follows Conventional Commits format |

### Branch workflow

1. Create a feature branch: `git switch -c feat/SCRUM-42-invoice-page`
2. Commit with Conventional Commits: `feat(pos): add invoice page`
3. Run tests locally: `docker compose up -d db-primary db-replica && ./mvnw verify`
4. Push and open a PR into `main` — CI must be green + 1 approval
5. Squash and merge, delete the branch, move Jira to Done

---

## 🤝 Contributing

1. **Follow the rules:** [GUIDELINES.md](GUIDELINES.md) covers architecture, design patterns, security, and the review checklist
2. **Commit messages:** [COMMIT.md](COMMIT.md) — Conventional Commits with scoped types. Run `git config core.hooksPath .githooks` after cloning
3. **Tests pass:** Never push with failing tests. Never disable a test to get green
4. **Review checklist** (from GUIDELINES.md §12):
   - [ ] Controllers contain no business logic
   - [ ] Modules talk only through public service interfaces or events
   - [ ] New behaviour added via new classes, not `if`/`switch` chains
   - [ ] Dependencies are interfaces, injected through constructors
   - [ ] Endpoints restricted by role; passwords hashed
   - [ ] Money uses `BigDecimal`; schema changes are Liquibase changesets
   - [ ] Read-only methods annotated `@Transactional(readOnly = true)`
   - [ ] Tests added/updated and pass

---

## 📚 Course Deliverables

| Document | Location |
| :--- | :--- |
| Software Requirements Specification (SRS) | [docs/srs/srs-v1.md](docs/srs/srs-v1.md) |
| Architecture Decision Records | [docs/decisions.md](docs/decisions.md) |
| UML Diagrams (use case, class, ERD, sequence, activity) | [docs/uml/](docs/uml/) |
| Sprint Plan & Jira Backlog | [docs/plan/jira-scrum-plan.md](docs/plan/jira-scrum-plan.md) |
| WBS, Estimates, Schedule & Risk Register | [docs/plan/wbs-estimate-schedule-risks.md](docs/plan/wbs-estimate-schedule-risks.md) |
| Basis Path Tests | [docs/testing/basis-paths.md](docs/testing/basis-paths.md) |

---

## 👥 Team

**Team Astro** — CSE 3219/3220, KUET

| Name | Role | Focus |
| :--- | :--- | :--- |
| **Rafsan Jani** | Developer | Core features, architecture |
| **MD. Shahriar Hossain Prottoy** | Developer | Backend, security, monitoring |
| **MD. Shahariar Emon Saikat** | Developer | Frontend, UI, dashboards |

---

## 📄 License

This repository does not currently specify a license. All rights reserved by the authors.
