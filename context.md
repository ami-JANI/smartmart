# SmartMart Project Context

This file gives any AI assistant (Claude, Codex, or another tool) and any new team member the full picture of SmartMart in one read. Load it at the start of a session, before you plan or change anything.

It summarises and links. It does not replace the rule files:

| File | Read it for |
| --- | --- |
| [CLAUDE.md](CLAUDE.md) | How to work: when to ask, how much to build, how to prove a change is done |
| [GUIDELINES.md](GUIDELINES.md) | What to build: stack, architecture, patterns, REST, security, data, testing, review checklist |
| [COMMIT.md](COMMIT.md) | How to write commit messages |
| [docs/decisions.md](docs/decisions.md) | Design decisions already made about the system |

If this file and one of those disagree, the rule file wins. Fix this file in a `docs(repo)` commit.

The file has two parts:

1. **Project context** (everything above [Change log](#change-log)). Written by people. Update it when something lasting changes: scope, a team rule, the stack, a sprint, a major status change.
2. **Change log** (the last section). Written by the `Context log` GitHub Actions workflow. After every push to `main` it appends the pushed commits with their authors and Jira keys. Do not edit it by hand.

## At a glance

| | |
| --- | --- |
| Project | SmartMart: An Integrated Super Shop Management System |
| What it is | A management system for **one supermarket branch**: point of sale, inventory, purchasing, user management, reporting |
| Inspiration | Bangladeshi chains such as Shwapno, Agora and Unimart (no affiliation) |
| Course | CSE 3219 Software Engineering and Project Management, and CSE 3220 (its lab) |
| Team | Three students, 10-week plan starting Sun 1 Nov 2026 |
| Repository | <https://github.com/ami-JANI/smartmart>, one branch: `main` |
| Tracker | Jira, space `SCRUM` on [smartmart.atlassian.net](https://smartmart.atlassian.net/jira/software/projects/SCRUM/boards/1/backlog) |
| Stack | Java 17, Spring Boot 3, PostgreSQL 16 (primary + streaming replica), Liquibase, Thymeleaf, Maven wrapper, Docker Compose |

## Team

| Short name | Jira name | GitHub | Area |
| --- | --- | --- | --- |
| Jani | Rafsan Jani | `ami-JANI` (also commits as `janir`) | Backend: foundation plumbing, auth, checkout; class diagram and ERD |
| Prottoy | MD. SHAHRIAR HOSSAIN PROTTOY | `Shahriar1290` | Backend: replica routing, catalogue, cart, inventory alerts; SRS v1 |
| Emon | MD. SHAHARIAR EMON SAIKAT | `ShahariarEmon112` | Frontend (Thymeleaf pages for each story), plus diagram and plan tasks |

Product Owner and Scrum Master rotate each sprint. The full split is in [docs/plan/jira-scrum-plan.md](docs/plan/jira-scrum-plan.md) section 4. The team uses both Claude and Codex.

## Scope

- **In scope:** one branch, four roles (Administrator, Manager, Cashier, Inventory staff), and five core modules, built in this priority order:
  1. Point of sale: barcode cart, discounts, checkout, payment recording, invoice
  2. Inventory: categories, products, stock batches with expiry, low-stock and near-expiry alerts
  3. Purchasing: suppliers, purchase orders, receiving, returns to suppliers
  4. User management: login, role-based access, user administration (built first as the foundation)
  5. Reporting: sales, purchase, inventory and profit summaries, dashboard
- **Stretch goals** (only after all five modules work): loyalty points, customer returns, audit logs, scanner hardware, multiple payment methods, backup and recovery, forecasting.
- **Out of scope:** multi-branch and enterprise features, microservices, payment gateways, automatic database failover. These appear only as future work in the report.
- **On hold for future work** (team decision, Nov 2026): buy-X-get-Y promotions and the manager screens for discounts (SRS Q1, Q3). Ask before starting any of these. SCRUM-36 (purchase orders) was taken off hold on 25 Nov 2026; its requirements are FR-PUR-4 to 8 in the SRS and the service is built, the pages are SCRUM-54. The daily sales report (SCRUM-37, SCRUM-55) was taken off hold on 23 Nov 2026 and built ([docs/decisions.md](docs/decisions.md)). SCRUM-30 (percentage and flat discounts) and SCRUM-31 (cash sale) were taken off hold on 17 Nov 2026 and built without promotions ([docs/decisions.md](docs/decisions.md)).

Requirements and acceptance criteria: [docs/srs/srs-v1.md](docs/srs/srs-v1.md).

## Architecture

- **Modular monolith**, one Spring Boot application, layered inside each module: `web` → `service` → `repository`, with `domain` for entities ([GUIDELINES.md](GUIDELINES.md) section 3).
- Packages: `com.smartmart.{auth, inventory, pos, purchasing, reports}` plus `com.smartmart.common` (`config`, `error`). Modules talk to each other only through service interfaces, never another module's repository.
- **Course patterns are required where GUIDELINES.md section 5 lists them**: Strategy (discounts, payments), Observer (stock alerts), Factory Method, Singleton (store settings), Facade (checkout), State (purchase orders, sales), Decorator (receipts), Adapter, Memento (cart undo), plus the Lec 5 modelling patterns. Do not add patterns it does not ask for.
- REST under `/api/v1`, one JSON error shape, BCrypt passwords, default-deny role checks on every page and endpoint, `BigDecimal` for money, Liquibase for every schema change, cyclomatic complexity V(G) ≤ 10 with basis path tests for the critical methods.

### Two database servers

A firm team decision, not a stretch goal ([docs/decisions.md](docs/decisions.md), [GUIDELINES.md](GUIDELINES.md) section 8.2):

- `db-primary` takes every write. `db-replica` is a hot standby fed by **asynchronous streaming replication**.
- A routing `DataSource` (`AbstractRoutingDataSource` wrapped in `LazyConnectionDataSourceProxy`) sends `@Transactional(readOnly = true)` to the replica and everything else to the primary. Only `common/config` knows the words "primary" and "replica".
- Reads that must see a fresh write (login and roles, stock during checkout, the invoice right after a sale, purchase order state checks) must not be `readOnly`.
- Liquibase runs only on the primary. Failover is manual: promote the replica, repoint both URLs, restart.
- `compose.yaml` and `db/` start both servers locally.

## Working rules (short form)

Full text in [CLAUDE.md](CLAUDE.md) section 0 and [COMMIT.md](COMMIT.md).

- `main` is the only branch. Commit and push straight to `main`. No feature branches or pull requests unless the team asks.
- Pull before you start and before you push. The `Context log` workflow adds a commit to `main` after each push, so your local `main` is usually one commit behind after pushing.
- Commit messages follow Conventional Commits as in COMMIT.md: `type(scope): description`, header at most 72 characters, scopes are the module names (`pos`, `inventory`, `purchasing`, `auth`, `reports`, `db`, `ui`, `api`, `srs`, `uml`, `plan`, `deps`, `repo`). Enable the hook once: `git config core.hooksPath .githooks`.
- Put the Jira key in a footer: `Refs: SCRUM-28`.
- **Commit messages never mention Claude or any AI tool**: no `Co-Authored-By` trailer for an assistant, no session links, no "generated by" text.
- One change per commit. Changes to GUIDELINES.md or COMMIT.md go in their own `docs(repo)` commit with the reason.
- Do not add a library without team agreement. Do not disable or skip a failing test.

## Process and Jira

- Light Scrum: one-week sprints, Sunday to Saturday. Stand-up each class day (Sun to Thu). Review and retro on Thursday.
- Jira site `smartmart.atlassian.net`, space Team Astro, key `SCRUM`, board 1. Workflow: To Do → In Progress → In Review → Done. Review happens before the push, because there are no pull requests.
- Epics: SCRUM-5 Foundation, SCRUM-6 User management, SCRUM-7 Inventory, SCRUM-8 Point of sale, SCRUM-9 Purchasing, SCRUM-10 Reporting, SCRUM-11 Course documents.

| Sprint | Dates | Goal |
| --- | --- | --- |
| SM Sprint 1 | 1 to 7 Nov 2026 | Skeleton on two databases, admin login, requirements agreed |
| SM Sprint 2 | 8 to 14 Nov 2026 | Access by role; product catalogue and stock |
| SM Sprint 3 | 15 to 21 Nov 2026 | First cash sale end to end: barcode, cart, discount, payment, invoice, and stock goes down |

Definition of Ready, Definition of Done and the full backlog: [docs/plan/jira-scrum-plan.md](docs/plan/jira-scrum-plan.md).

## Current status

Snapshot as of **17 Nov 2026** (Sprint 3). For anything newer, read the [Change log](#change-log) below and the Jira board.

| Module | Built |
| --- | --- |
| User management | Login, default-deny access by role, user administration, lockout after repeated failed logins (with the wait time shown) |
| Inventory | Category tree, products with unique barcodes, stock batches with expiry, first-expiry-first-out deduction, low-stock and near-expiry alerts (`stock_alerts` table, 7-day default) |
| Point of sale | Barcode and weighed-item cart with undo; the cashier picks one percentage or flat discount (seeded through Liquibase); cash checkout through `CheckoutFacade` saves the sale, its lines and payment and deducts stock in one transaction. The invoice page (`/pos/invoices/{saleId}`, SCRUM-32) shows the sale straight after payment. Members are registered by phone (`/pos/members`, `customers` table) and can be added to a sale, which records `sales.customer_id`. No buy-X-get-Y promotions. |
| Purchasing | Supplier management. Purchase orders with the State pattern (Draft, Submitted, Partially received, Received, Closed, Cancelled) and receiving into stock batches: service and tests (SCRUM-36) and pages at `/purchase-orders` (SCRUM-54): list by status, new draft, order page with the actions its state and the user's role allow, and receiving. Returns to suppliers not built. |
| Reporting | Dashboard (`/dashboard`) for every role, showing only what each role covers (cashier: today and members; stock: inventory; admin: accounts; manager: all). For managers: today and month-to-date sales against the same days last month, a 30-day chart, best sellers, top members, products selling less than last month, and live stock for every product. Daily sales report (`/reports/daily`, SCRUM-55): totals and products sold on a chosen day, print-friendly. Both read the replica. |

Jira on 17 Nov 2026: SCRUM-30 and 31 (Jani) are In Review; SCRUM-32 (invoice page, Prottoy) is To Do; SCRUM-28, 29 and 33 are Done.

## Course deliverables

| Document | Where |
| --- | --- |
| SRS | [docs/srs/srs-v1.md](docs/srs/srs-v1.md) |
| Design decisions | [docs/decisions.md](docs/decisions.md) |
| UML (use case, class, ERD, checkout sequence and activity) | [docs/uml/](docs/uml/), PlantUML |
| Sprint plan, WBS, estimate, schedule, risks | [docs/plan/](docs/plan/) |
| Basis path tests | [docs/testing/basis-paths.md](docs/testing/basis-paths.md) |

Release documentation is still to come. The course grades SRS, UML, GRASP, the patterns above, estimation, scheduling, risk, testing with cyclomatic complexity, security, Git use and release docs.

## Running and testing

Setup steps are in the [README](README.md#getting-started). Things that trip people up:

- The tests need **both** databases running (`docker compose up -d db-primary db-replica`, then `./mvnw verify`). Some tests check that read-only work reaches the replica.
- On Jani's Windows machine an Oracle listener holds port 8080. Run the app with `--server.port=8090`, or map `"8090:8080"` in `compose.yaml` locally without committing it.
- Without Docker (for example in a cloud sandbox), one local PostgreSQL 16 can serve both URLs. Everything passes except `DataSourceRoutingTests.readOnlyTransactionRunsOnReplica`, which needs a real replica.
- `.env` holds the database passwords and the first admin login. Copy it from `.env.example`; never commit it.
- **Nothing to see after logging in?** A fresh database has only the administrator, who may open only the Users page. `LIQUIBASE_CONTEXTS=demo` (in `.env.example`) loads a sample shop and logins `cashier`, `stock`, `manager` and `demo` with every role (password `smartmart-demo`); see the README section "Demo data". After login every user reaches a home page with a card for each part their roles may open.
- Monitoring (SCRUM-59 to SCRUM-62): `/actuator/health` is public; Prometheus (port 9090) and Grafana (port 3000) run in Compose. They need `METRICS_PASSWORD` and `GRAFANA_ADMIN_PASSWORD` in `.env` (README, "Monitoring").
- CI (`.github/workflows/build.yml`) runs `./mvnw verify` with both databases on every push to `main`. `commit-messages.yml` checks every pushed commit message.

## Change log

Appended automatically by `.github/workflows/context-log.yml` after each push to `main`: one entry per push, newest last, listing each commit with its author and Jira keys. Do not edit by hand; put lasting facts in the sections above instead.

<!-- context-log: entries below this line are generated -->

### 2026-11-14, pushed by ami-JANI

- `56a4076` docs(repo): add context.md with the full project context (ami-JANI)
- `f78da19` ci: append each push to main to the context.md change log (ami-JANI)
- `41577c6` docs(repo): point assistants at context.md first (ami-JANI)

### 2026-11-14, pushed by ShahariarEmon112

- `c562469` feat(ui): show an alerts link with the open count in the menu (ShahariarEmon112), SCRUM-53
- `3b19ae8` fix(ui): match the alerts page to the other lists (ShahariarEmon112), SCRUM-53

### 2026-11-17, pushed by ami-JANI

- `c90da85` feat(db): create discounts table (ami-JANI), SCRUM-30
- `2d85258` feat(pos): build each discount's strategy from its stored type (ami-JANI), SCRUM-30
- `0f012b3` feat(pos): list the discounts that are valid today (ami-JANI), SCRUM-30
- `b10ba88` feat(pos): let the cashier pick one discount on the cart (ami-JANI), SCRUM-30
- `05a2768` feat(db): seed the first two discounts (ami-JANI), SCRUM-30
- `7773895` feat(db): create sales, sale lines and payments tables (ami-JANI), SCRUM-31
- `a7cffc4` feat(pos): map a completed sale with its lines and payment (ami-JANI), SCRUM-31
- `5ce333f` feat(pos): take cash through a payment method strategy (ami-JANI), SCRUM-31
- `4421481` feat(pos): complete a sale in one transaction through CheckoutFacade (ami-JANI), SCRUM-31
- `4a9c350` feat(pos): complete the sale from the cash payment page (ami-JANI), SCRUM-31, SCRUM-32
- `3f1dc72` docs(pos): record the first discount and cash sale decisions (ami-JANI), SCRUM-30, SCRUM-31
- `99d3c13` docs(pos): add basis paths for discount calculation and checkout (ami-JANI), SCRUM-30, SCRUM-31
- `14f03dc` docs(uml): add discounts, sales and checkout to the erd and classes (ami-JANI), SCRUM-30, SCRUM-31
- `e68a587` docs(repo): update context.md for discounts and cash checkout (ami-JANI), SCRUM-30, SCRUM-31

### 2026-11-17, pushed by ShahariarEmon112

- `1d8b35d` feat(db): add sample shop data behind LIQUIBASE_CONTEXTS=demo (ShahariarEmon112)
- `cb2aac7` build: run the app container on Bangladesh time (ShahariarEmon112)
- `9e0ee4f` feat(pos): show the invoice straight after a sale (ShahariarEmon112), SCRUM-32, SCRUM-47
- `ecc153d` feat(ui): add a home page with a card for each role's work (ShahariarEmon112), SCRUM-38
- `80bb1e2` fix(ui): highlight the current menu item (ShahariarEmon112), SCRUM-39
- `ae2c18f` docs(repo): explain the demo data and the first login (ShahariarEmon112)

### 2026-11-17, pushed by ShahariarEmon112

- `1ff6682` feat(ui): tell visitors how to get an account (ShahariarEmon112), SCRUM-38

### 2026-11-17, pushed by ShahariarEmon112

- `ecf9224` feat(ui): add a shared set of inline svg icons (ShahariarEmon112), SCRUM-38
- `bb88711` feat(ui): redesign the layout with a gradient header and section colours (ShahariarEmon112), SCRUM-38
- `c3a07dd` feat(ui): add a welcome banner and coloured cards to home and login (ShahariarEmon112), SCRUM-38

### 2026-11-17, pushed by ShahariarEmon112

- `34a042a` feat(ui): label table cells so rows can become cards on phones (ShahariarEmon112), SCRUM-38
- `12eecb0` feat(ui): make every page responsive from 320px to desktop (ShahariarEmon112), SCRUM-38

### 2026-11-17, pushed by ShahariarEmon112

- `b9fc7e1` feat(db): add members and record the member on a sale (ShahariarEmon112)
- `a36b477` feat(pos): register members by phone and add them to a sale (ShahariarEmon112)
- `4f3bf71` feat(reports): add the manager dashboard (ShahariarEmon112)
- `0fe4aa3` feat(ui): add dashboard and members to the menu and home page (ShahariarEmon112)
- `52419ff` feat(db): add two months of demo sales and members (ShahariarEmon112)
- `84ebdda` docs(repo): record the members and dashboard decision (ShahariarEmon112)

### 2026-11-17, pushed by ShahariarEmon112

- `ebe78b2` fix(ui): remove blank space from the dashboard cards (ShahariarEmon112)

### 2026-11-23, pushed by ShahariarEmon112

- `d2b8286` feat(reports): add the daily sales report service (ShahariarEmon112), SCRUM-37
- `00b352c` feat(reports): add the printable daily sales report page (ShahariarEmon112), SCRUM-55
- `40a6b9b` feat(ui): add the daily report to the menu and home page (ShahariarEmon112), SCRUM-55
- `f669497` docs(repo): record the daily sales report decision (ShahariarEmon112), SCRUM-37, SCRUM-55

### 2026-11-25, pushed by ShahariarEmon112

- `a4192cc` feat(db): add a demo login with every role (ShahariarEmon112)
- `9d82722` feat(reports): show each role its own part of the dashboard (ShahariarEmon112)
- `ce5780d` feat(ui): link the dashboard for every role (ShahariarEmon112)
- `16d70ce` feat(ui): make the dashboard fill every row at any width (ShahariarEmon112)
- `4f1bb3f` docs(repo): record the per-role dashboard and the demo login (ShahariarEmon112)

### 2026-11-25, pushed by ami-JANI

- `41cb788` docs(srs): add purchase order requirements (ami-JANI), SCRUM-36
- `1dd0554` feat(db): add purchase order tables (ami-JANI), SCRUM-36
- `dc5958d` feat(purchasing): add purchase orders with the State pattern (ami-JANI), SCRUM-36
- `16ac27b` feat(purchasing): add the purchase order service (ami-JANI), SCRUM-36
- `a75b1dd` feat(purchasing): receive purchase orders into stock (ami-JANI), SCRUM-36
- `d3cbb19` docs(purchasing): add basis paths for purchase order receiving (ami-JANI), SCRUM-36
- `56fa904` docs(uml): add purchase orders to the class diagram and ERD (ami-JANI), SCRUM-36
- `d6c6601` docs(purchasing): record the purchase order decisions (ami-JANI), SCRUM-36, SCRUM-54
- `1521486` docs(repo): note purchase orders in context.md (ami-JANI), SCRUM-36, SCRUM-54
- `e4278a6` feat(purchasing): add the purchase order pages (ShahariarEmon112), SCRUM-54
- `3afed55` feat(ui): add purchase orders to the menu and home page (ShahariarEmon112), SCRUM-54
- `9241517` docs(repo): describe the purchase order pages (ShahariarEmon112), SCRUM-54

### 2026-11-27, pushed by ShahariarEmon112

- `d1ca3e8` build(deps): add actuator and the prometheus registry (ShahariarEmon112), SCRUM-59, SCRUM-60
- `372b4f3` feat(api): add actuator health checks and secure its endpoints (ShahariarEmon112), SCRUM-59, SCRUM-60
- `89303d5` feat(pos): count sales, revenue and checkout time (ShahariarEmon112), SCRUM-60
- `1d9424c` feat(inventory): publish the number of open stock alerts (ShahariarEmon112), SCRUM-60
- `dc7791a` build: add prometheus and grafana with provisioned dashboards (ShahariarEmon112), SCRUM-60, SCRUM-61
- `c199cb2` feat(ui): show admins system health and a monitoring link (ShahariarEmon112), SCRUM-62
- `377e390` docs(repo): document monitoring and its decisions (ShahariarEmon112), SCRUM-59, SCRUM-60, SCRUM-61, SCRUM-62

### 2026-11-27, pushed by Shahriar1290

- `73160df` feat(auth): grant admin full endpoint access and manager privileges (Shahriar1290), SCRUM-63

### 2026-11-27, pushed by Shahriar1290

- `f4402a8` feat(ui): display all navigation items and manager controls to admin (Shahriar1290), SCRUM-64

### 2026-11-27, pushed by Shahriar1290

- `65b1028` docs(repo): rewrite README with full project documentation (Shahriar1290)
