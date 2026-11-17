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
- **On hold for future work** (team decision, Nov 2026): buy-X-get-Y promotions and the manager screens for discounts (SRS Q1, Q3); SCRUM-36 purchase orders and SCRUM-37 daily sales report, whose acceptance criteria are still "to be refined" and are outside SRS v1. Ask before starting any of these. SCRUM-30 (percentage and flat discounts) and SCRUM-31 (cash sale) were taken off hold on 17 Nov 2026 and built without promotions ([docs/decisions.md](docs/decisions.md)).

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
| Point of sale | Barcode and weighed-item cart with undo; the cashier picks one percentage or flat discount (seeded through Liquibase); cash checkout through `CheckoutFacade` saves the sale, its lines and payment and deducts stock in one transaction. The invoice page (`/pos/invoices/{saleId}`, SCRUM-32) is not built yet. No buy-X-get-Y promotions. |
| Purchasing | Supplier management. Purchase orders, receiving and returns not built. |
| Reporting | Not started. |

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
