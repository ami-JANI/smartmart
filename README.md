# SmartMart: An Integrated Super Shop Management System

A management system for a single supermarket branch. It covers point of sale, inventory, purchasing, user management, and reporting.

> **Status: in development (first sprint of a 10-week plan).** Login, roles, user administration, categories, products, stock batches, suppliers and the POS cart work. Checkout, invoices, purchase orders and reports are not built yet. The table under [Modules](#modules) shows where each module stands.

## Overview

In a busy supermarket, sales, stock, and supplier deliveries are often recorded in different places. When they drift apart, stock counts become wrong. Shelves run empty without warning, expired items stay on sale, and nobody can say with confidence which products make money.

SmartMart aims to keep these workflows in one system so that each one updates the others. A sale reduces stock, a received delivery increases it, low-stock and expiry alerts come from the same stock records, and reports are built from recorded sales and purchases.

The project takes inspiration from supermarket chains in Bangladesh such as Shwapno, Agora, and Unimart. It has no affiliation with those businesses.

SmartMart is a three-person academic project for **CSE 3219: Software Engineering and Project Management** and **CSE 3220: Software Engineering and Project Management Laboratory**, with a planned development period of **10 weeks**. The scope is limited to one branch.

## Intended Users

| Role | Main work in the system |
| --- | --- |
| Administrator | Manages user accounts and role assignments |
| Manager | Reviews stock levels, expiry information, and reports |
| Cashier | Handles checkout at the point of sale |
| Inventory staff | Maintains products and stock, records supplier deliveries |

Each role can open only its own pages; the full rules are FR-AUTH-5 in the [SRS](docs/srs/srs-v1.md).

## Modules

The modules are listed in priority order. Requirements and acceptance criteria for the first four are in the [SRS](docs/srs/srs-v1.md).

| # | Module | Intended capabilities | Built so far |
| --- | --- | --- | --- |
| 1 | Point of sale | Barcode-based product selection, cart, checkout, discounts, payment recording, and invoice generation | Barcode and weighed-item cart with undo. Checkout, discounts, promotions and invoices are in progress. |
| 2 | Inventory | Product and category management, stock tracking, low-stock alerts, and batch and expiry-date monitoring | Categories, products, stock batches with expiry dates, and low-stock flags. Alerts are not built yet. |
| 3 | Purchasing | Supplier management, purchase orders, stock receiving, purchase records, and returns to suppliers | Suppliers. Purchase orders, receiving and returns are not built yet. |
| 4 | User management | Login and role-based access for the four roles above | Login, access by role, and user administration. The lockout after repeated failed logins is not built yet. |
| 5 | Reporting | Sales, purchase, inventory, and profit summaries, plus a dashboard | Not started. |

"Payment recording" means storing how a sale was paid. It does not imply a payment gateway or live payment processing. Barcode scanner hardware has not been specified.

### Intended workflow

1. Inventory staff maintain products, categories, and supplier records.
2. Received purchases are recorded, which increases available stock.
3. Cashiers complete checkout, which produces an invoice and reduces stock.
4. Managers review stock levels, expiry information, and reports.

### Stretch goals

These will be attempted only if time permits and are not committed deliverables: customer loyalty points, customer sales returns, audit logs, hardware barcode-scanner support, multiple payment methods, backup and recovery, and demand forecasting.

Multi-branch and other enterprise features are out of scope.

## Course Deliverables

Alongside the application, the project produces the course's software engineering documents. Release documentation is still to come.

| Document | Where |
| --- | --- |
| Requirements (SRS) | [docs/srs/srs-v1.md](docs/srs/srs-v1.md) |
| Design decisions | [docs/decisions.md](docs/decisions.md) |
| UML: use case, class, ERD, checkout sequence and activity | [docs/uml/](docs/uml/) (PlantUML sources) |
| Sprint plan, WBS, estimate, schedule and risk register | [docs/plan/](docs/plan/) |
| Basis path tests | [docs/testing/basis-paths.md](docs/testing/basis-paths.md) |

## Getting Started

The stack is Java 17, Spring Boot 3, PostgreSQL 16, Thymeleaf and Maven ([GUIDELINES.md](GUIDELINES.md) section 2). The application uses two database servers, a primary and a streaming replica, which Docker Compose starts for you (GUIDELINES.md section 8.2).

### What you need

- JDK 17 or newer
- Docker Desktop, running
- Git

Maven is not needed: the project includes the Maven wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

### First time

```sh
git clone https://github.com/ami-JANI/smartmart.git
cd smartmart
git config core.hooksPath .githooks
git config commit.template .gitmessage
cp .env.example .env
```

`.env` holds the database passwords and the first administrator's login (`ADMIN_USERNAME`, `ADMIN_PASSWORD`). Change the placeholder values, and never commit `.env`. If your `.env` is older than the login feature, add the two `ADMIN_` lines from `.env.example`: without them no administrator is created, and nobody can log in.

### Run everything in Docker

```sh
docker compose up -d --build
```

This starts both databases and the application. Open <http://localhost:8080> and log in with `ADMIN_USERNAME` and `ADMIN_PASSWORD`. As administrator you can then create accounts for the other roles.

### Demo data

To see every part of the app working straight away, `.env.example` turns on a small sample shop with `LIQUIBASE_CONTEXTS=demo`: 12 products in a category tree, stock batches (some low or close to expiry, so alerts show), two suppliers, and one login per role:

| Username | Role | Lands on |
| --- | --- | --- |
| `cashier` | Cashier | Point of sale: scan `2000000000103` (bath soap) or `2000000000011` (rice, by weight), add member `01711000101`, pay cash, see the invoice; register members under Members |
| `stock` | Inventory staff | Products, categories, suppliers, stock alerts |
| `manager` | Manager | Dashboard: sales this month against last month, a 30-day chart, best sellers, top members, sales drops and live stock; a printable daily sales report; stock and alerts, read only |

All three use the password `smartmart-demo`. The administrator is still `ADMIN_USERNAME`.

The demo also has six members (phones `01711000101` to `01711000106`) and about two months of past sales, so the dashboard has something to show.

- If your `.env` is older, add the line `LIQUIBASE_CONTEXTS=demo` from `.env.example` and restart; the data is loaded once.
- Remove that line before using SmartMart for a real shop. The tests and CI never load the demo data.
- To start again from an empty database, run `docker compose down -v` (this deletes all data), then `docker compose up -d --build`.

### Run the application on your machine

Useful while developing, because a code change needs only a restart:

```sh
docker compose up -d db-primary db-replica
./mvnw spring-boot:run
```

If port 8080 is already in use (for example by an Oracle database service on Windows), run it on another port:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
```

For Docker Compose, change `"8080:8080"` under `app` in `compose.yaml` to, say, `"8090:8080"` on your machine only, and do not commit that change.

### Run the tests

```sh
docker compose up -d db-primary db-replica
./mvnw verify
```

The tests need both databases running, because some of them check that read-only work reaches the replica. They clean up the data they create. GitHub Actions runs the same build on every push to `main`.

### Stop

```sh
docker compose down
```

This keeps the data. `docker compose down -v` also deletes both databases.

## Contributing

- Write every commit message in the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) format described in [COMMIT.md](COMMIT.md). After cloning, run `git config core.hooksPath .githooks` and `git config commit.template .gitmessage` once so Git checks your messages locally. GitHub Actions also checks every pushed commit.
- Use GitHub issues to report problems or propose changes to the agreed scope. Discuss substantial changes with the team first.
- Follow the design and coding rules in [GUIDELINES.md](GUIDELINES.md), and check each change against its review checklist.
- Commit directly to `main`, which is the only branch. Pull before you start and push only when the build and tests pass.

## License

This repository does not currently specify a license.
