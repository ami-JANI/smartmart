# SmartMart Software Requirements Specification, v1

| | |
| --- | --- |
| Version | 1.0 (draft for team review) |
| Date | 2026-11-01 |
| Covers | User management (Auth), Inventory, Point of sale (POS) |
| Jira | SCRUM-18 |

## 1. Introduction

### 1.1 Purpose

This document states what SmartMart must do for its first three modules, so that each Jira story has testable acceptance criteria. It is the reference for design, testing and the sprint demos. Purchasing and Reporting are specified in a later version.

### 1.2 Scope

SmartMart is a management system for **one supermarket branch** (README). Version 1 of this SRS covers:

- **User management:** login, roles and user administration.
- **Inventory:** categories, products, stock batches with expiry dates, low-stock thresholds.
- **Point of sale:** cart, discounts and promotions, cash payment, invoice, stock deduction.

Out of scope for v1: purchasing, reports and dashboard (later SRS versions); multi-branch features; payment gateways; scale and scanner hardware drivers; the stretch goals in the README.

### 1.3 Definitions

| Term | Meaning |
| --- | --- |
| Batch | One delivery of a product, with its own quantity, cost and expiry date (`StockBatch`) |
| FEFO | First-expiry-first-out: stock is taken from the batch that expires soonest |
| Sale unit | How a product is sold: `PIECE` (whole units) or `KG` (weight) |
| Discount | A manager-defined percentage or flat amount off a sale |
| Promotion | A manager-defined "buy X get Y free" offer on one product |
| Primary / replica | The read-write database server and its read-only streaming copy (GUIDELINES.md section 8.2) |
| BDT | Bangladeshi taka, the store currency |

### 1.4 References

- [README.md](../../README.md): scope, roles, modules
- [GUIDELINES.md](../../GUIDELINES.md): stack, architecture, patterns, security, data, testing
- [docs/decisions.md](../decisions.md): decisions behind sections 3.2 and 3.3
- [docs/plan/jira-scrum-plan.md](../plan/jira-scrum-plan.md): sprint plan and Jira keys

## 2. Overall description

### 2.1 Users

| Role | Main work | Uses |
| --- | --- | --- |
| Administrator | Manages user accounts and roles | User management |
| Manager | Reviews stock and expiry; defines discounts and promotions | Inventory views, discounts, promotions |
| Cashier | Runs checkout | POS |
| Inventory staff | Maintains categories, products and stock batches | Inventory |

A user can hold more than one role. Access rules are in FR-AUTH-5.

### 2.2 Operating environment

- A Spring Boot 3 web application with server-rendered Thymeleaf pages, used in a desktop browser at the till and in the back office.
- Two PostgreSQL 16 servers: a primary for writes and a streaming replica for read-only queries (GUIDELINES.md section 8.2).
- Runs locally with Docker Compose.

### 2.3 Constraints

- The technology stack is fixed by GUIDELINES.md section 2.
- Money is stored as `NUMERIC(12,2)` and handled as `BigDecimal`, in BDT.
- Store settings (store name, VAT rate, currency, receipt header, default low-stock threshold) are read at startup and do not change while the application runs (GUIDELINES.md section 5.4).

### 2.4 Assumptions

- A barcode scanner acts as a keyboard: it types the barcode and presses Enter. No scanner driver is needed.
- Weighed goods are weighed on a separate scale, and the cashier types the weight.
- Payment is cash only in v1; other methods are stretch goals.

## 3. Functional requirements

Each requirement has an ID, a statement and acceptance criteria (AC). The Jira column links it to the story that delivers it.

### 3.1 User management (AUTH)

| ID | Requirement | Jira |
| --- | --- | --- |
| FR-AUTH-1 | A user logs in with username and password. | SCRUM-17 |
| FR-AUTH-2 | A logged-in user can log out. | SCRUM-17 |
| FR-AUTH-3 | The first administrator account is created at startup from environment variables. | SCRUM-17 |
| FR-AUTH-4 | Passwords are stored only as BCrypt hashes. | SCRUM-17 |
| FR-AUTH-5 | Every page and endpoint is restricted by role; anything not allowed is denied. | SCRUM-20 |
| FR-AUTH-6 | An administrator can create, edit and deactivate users. | SCRUM-21 |
| FR-AUTH-7 | An administrator can assign one or more roles to a user. | SCRUM-21 |
| FR-AUTH-8 | Repeated failed logins are temporarily blocked. | SCRUM-22 (later sprint) |

**Acceptance criteria**

- **FR-AUTH-1**
  - Correct username and password: the user reaches the home page for their role.
  - Wrong password or unknown username: the same message "Invalid username or password" appears, so the system does not reveal which one was wrong.
  - A deactivated user cannot log in.
  - Login and role loading read from the primary, so a role change applies at the next login.
- **FR-AUTH-2**
  - After logout, opening any protected page leads to the login page.
- **FR-AUTH-3**
  - With an empty database, starting the application creates one Administrator from `ADMIN_USERNAME` and `ADMIN_PASSWORD`.
  - If that user already exists, startup does not change it.
- **FR-AUTH-4**
  - The `users` table holds a BCrypt hash with strength 12 or more; the plain password appears nowhere in the database or logs.
- **FR-AUTH-5**
  - Access follows this table; every other combination is refused.

    | Role | Allowed |
    | --- | --- |
    | Administrator | User and role management |
    | Manager | Stock and expiry views, discounts and promotions |
    | Cashier | POS checkout, own sales |
    | Inventory staff | Categories, products, stock batches |

  - Not logged in: **401** for an API call, redirect to login for a page.
  - Logged in with the wrong role: **403** and a "not allowed" page.
- **FR-AUTH-6**
  - Username is required, unique, 3 to 50 characters.
  - Password is at least 8 characters.
  - Creating a user with an existing username fails with "Username already taken" (**409** for the API).
  - Deactivating a user keeps their sales history.
  - An administrator cannot deactivate their own account.
- **FR-AUTH-7**
  - A user must have at least one role.
  - Removing the last Administrator role in the system is refused.
- **FR-AUTH-8**
  - After 5 failed attempts within one minute for the same username and IP address, further attempts get **429** with a `Retry-After` header until the minute has passed.

### 3.2 Inventory (INV)

| ID | Requirement | Jira |
| --- | --- | --- |
| FR-INV-1 | Inventory staff manage a tree of categories. | SCRUM-23 |
| FR-INV-2 | Inventory staff add and edit products. | SCRUM-24 |
| FR-INV-3 | Every product has a unique barcode. | SCRUM-24 |
| FR-INV-4 | Products can be listed, searched and filtered. | SCRUM-24 |
| FR-INV-5 | Each product is sold by piece or by weight. | SCRUM-24 |
| FR-INV-6 | Inventory staff record stock batches with an expiry date. | SCRUM-25 |
| FR-INV-7 | The system shows each product's total stock. | SCRUM-25 |
| FR-INV-8 | Each product has a low-stock threshold, its own or the store default. | SCRUM-24, SCRUM-34 |
| FR-INV-9 | The system warns about low stock and batches close to expiry. | SCRUM-34 (later sprint) |

**Product fields**

| Field | Rule |
| --- | --- |
| Name | Required, 1 to 120 characters |
| Barcode | Required, unique, 4 to 20 digits |
| Category | Required, an existing category |
| Sale unit | Required: `PIECE` or `KG` |
| Selling price | Required, greater than 0, BDT with 2 decimals, VAT included, per piece or per kg |
| Low-stock threshold | Optional; a quantity in the product's sale unit, 0 or more |
| Active | Yes or no; inactive products cannot be sold |

**Acceptance criteria**

- **FR-INV-1**
  - A category has a name (required, unique among its siblings) and an optional parent, for example Grocery → Rice → Miniket.
  - Moving a category under itself or under one of its own descendants is refused.
  - A category that has products or child categories cannot be deleted.
- **FR-INV-2**
  - Every field rule in the product fields table is checked; each broken rule shows a message next to its field (**400** for the API).
  - Changing a price affects only sales made afterwards.
- **FR-INV-3**
  - Saving a product with a barcode another product already has fails with "Barcode already in use" (**409**).
- **FR-INV-4**
  - The list is paged (default 20 per page), sortable by name or price, and filterable by category, by name or barcode text, and by low stock.
  - The list reads from the replica, so a product saved a moment ago may take up to a second to appear.
- **FR-INV-5**
  - `PIECE` quantities are whole numbers.
  - `KG` quantities have at most 3 decimal places.
  - Any other quantity is refused with a message.
- **FR-INV-6**
  - A batch belongs to one product and has a batch number, quantity (greater than 0, in the product's sale unit), unit cost (BDT, 0 or more) and expiry date.
  - The expiry date may be empty only for goods that do not expire.
  - The batch does not copy the product's name or price.
- **FR-INV-7**
  - Total stock is the sum of all batch quantities for the product.
  - No batch quantity can go below 0.
  - The product page lists batches with quantity and expiry, soonest expiry first.
- **FR-INV-8**
  - If the product has a threshold, it is used; otherwise the store default from settings is used.
  - A product is low on stock when its total stock is at or below the threshold that applies.
- **FR-INV-9**
  - Detailed in a later SRS version. The low-stock rule is FR-INV-8; "close to expiry" needs a number of days, which is open question Q2.

### 3.3 Point of sale (POS)

| ID | Requirement | Jira |
| --- | --- | --- |
| FR-POS-1 | The cashier adds a product to the cart by barcode. | SCRUM-28 |
| FR-POS-2 | The cashier enters the weight for a weighed product. | SCRUM-28 |
| FR-POS-3 | The cashier changes a line's quantity or removes a line. | SCRUM-28 |
| FR-POS-4 | The cashier undoes the last cart change. | SCRUM-29 |
| FR-POS-5 | The cart shows line totals, discount, total and the VAT included. | SCRUM-28, SCRUM-30 |
| FR-POS-6 | Managers define discounts and buy-X-get-Y promotions. | Not planned yet (Q1) |
| FR-POS-7 | Promotions apply automatically to their product. | SCRUM-30 |
| FR-POS-8 | The cashier applies at most one discount to a sale. | SCRUM-30 |
| FR-POS-9 | The cashier takes cash and the system shows the change due. | SCRUM-31 |
| FR-POS-10 | Completing a sale records payment, invoice and stock deduction together. | SCRUM-31 |
| FR-POS-11 | Stock is deducted first-expiry-first-out. | SCRUM-31 |
| FR-POS-12 | A sale that needs more stock than exists is refused. | SCRUM-31 |
| FR-POS-13 | The invoice is shown straight after the sale. | SCRUM-32 |

**Acceptance criteria**

- **FR-POS-1**
  - Scanning or typing a known barcode of an active `PIECE` product adds 1 unit; scanning it again increases that line by 1.
  - An unknown barcode, or an inactive product, shows "Product not found" and changes nothing.
- **FR-POS-2**
  - Scanning a `KG` product asks for the weight. A weight of 0, a negative weight or more than 3 decimals is refused.
  - The line total is price per kg × weight, rounded to 2 decimals.
- **FR-POS-3**
  - A `PIECE` quantity must be at least 1; setting it lower is refused.
  - Removing a line takes it out of the cart.
- **FR-POS-4**
  - Undo reverses the last add, quantity change, removal or applied discount, one step at a time.
  - At least the last 20 changes can be undone.
  - After the sale completes, the undo history is cleared.
- **FR-POS-5**
  - Line total = unit price × quantity, rounded half-up to 2 decimals.
  - Subtotal = sum of line totals.
  - Total = subtotal − promotion savings − discount.
  - VAT included = total × rate ÷ (100 + rate), rounded half-up to 2 decimals, with the rate from store settings.
- **FR-POS-6**
  - A discount has a name, a type (percentage or flat), a value, and start and end dates.
    - A percentage is greater than 0 and at most 100.
    - A flat amount is greater than 0.
  - A promotion has a product (sale unit `PIECE`), X ≥ 1, Y ≥ 1, and start and end dates.
  - Only managers can create, change or end them.
  - A product can have at most one active promotion at a time.
- **FR-POS-7**
  - While a promotion is active, for every X + Y units of its product in the cart, Y units are free; the free units show as a separate line at 0.
  - Example, buy 2 get 1: 3 units → pay for 2; 5 units → pay for 4; 6 units → pay for 4.
  - The cashier scans every unit the customer takes, free ones included, so all of them are deducted from stock.
- **FR-POS-8**
  - The cashier can pick one active discount from a list. Cashiers cannot type their own amount.
  - The discount applies only to the lines that are not on a promotion (FR-POS-7). A promoted product never gets an extra discount.
  - Choosing another discount replaces the first one.
  - The discount is never negative and never more than the total of the lines it applies to.
    - Example: 10% on 500.00 is 50.00.
    - Example: a flat 100.00 on 60.00 of eligible lines is 60.00.
- **FR-POS-9**
  - Cash received must be at least the total; otherwise the sale cannot be completed.
  - Change due = cash received − total.
- **FR-POS-10**
  - One action completes the sale. The payment record, the invoice and every stock deduction are saved together, or none of them is.
  - A failure leaves the cart as it was, so the cashier can try again.
- **FR-POS-11**
  - Each line's quantity is taken from the product's batches in order of expiry date, soonest first; batches without an expiry date come last.
  - Expired batches are skipped.
  - If one batch is not enough, the rest comes from the next batch.
- **FR-POS-12**
  - If any line needs more than the product's unexpired stock, the sale is refused (**409**) with "Not enough stock for <product>", and nothing is saved.
- **FR-POS-13**
  - The invoice shows:
    - store name and receipt header from settings
    - invoice number and date and time of the sale
    - cashier name
    - each line with quantity, unit price and line total, with free promotion units marked
    - promotion savings, the discount, the total and the VAT included
    - cash received and change due
  - It is read from the primary, so it is complete straight after the sale.
  - It has a print-friendly layout.

## 4. Non-functional requirements

| ID | Requirement |
| --- | --- |
| NFR-1 Security | BCrypt passwords (FR-AUTH-4); role checks on every page and endpoint (FR-AUTH-5); input validated on every request; secrets come only from environment variables; no stack traces or SQL in error responses (GUIDELINES.md sections 6 and 7). |
| NFR-2 Performance | With 10,000 products and 1,000 sales in the database, on a developer laptop: adding an item to the cart responds within 1 second, and completing a sale within 2 seconds. |
| NFR-3 Data integrity | Money uses `BigDecimal` and `NUMERIC(12,2)`; stock never goes negative; a sale is one transaction (FR-POS-10). |
| NFR-4 Availability | If the primary fails, the replica can be promoted by hand as in GUIDELINES.md section 8.2. Up to the last few seconds of sales may need to be re-entered from receipts. |
| NFR-5 Consistency | Lists and searches may run up to about a second behind; checkout, the invoice, login and roles always read current data from the primary. |
| NFR-6 Usability | The checkout can be done with the keyboard and scanner only. Every error message says what to fix. |
| NFR-7 Maintainability | Layered modules and the design patterns in GUIDELINES.md sections 3 and 5; no method above cyclomatic complexity 10; basis path tests for discount calculation, checkout and stock deduction (section 9). |
| NFR-8 Portability | The whole system starts with `docker compose up` on Windows, macOS or Linux. |

## 5. Traceability to Jira

| Jira | Requirements |
| --- | --- |
| SCRUM-17 Login | FR-AUTH-1 to 4 |
| SCRUM-20 Access by role | FR-AUTH-5 |
| SCRUM-21 User admin | FR-AUTH-6, 7 |
| SCRUM-22 Login lockout | FR-AUTH-8 |
| SCRUM-23 Category tree | FR-INV-1 |
| SCRUM-24 Products | FR-INV-2 to 5, 8 |
| SCRUM-25 Stock batches | FR-INV-6, 7 |
| SCRUM-28 Cart | FR-POS-1 to 3, 5 |
| SCRUM-29 Undo | FR-POS-4 |
| SCRUM-30 Discounts | FR-POS-5, 7, 8 |
| SCRUM-31 Checkout | FR-POS-9 to 12 |
| SCRUM-32 Invoice | FR-POS-13 |
| SCRUM-34 Stock alerts | FR-INV-8, 9 |
| none yet | FR-POS-6 |

## 6. Open issues

| # | Issue | Proposal |
| --- | --- | --- |
| Q1 | No Jira story covers the manager screens for creating discounts and promotions (FR-POS-6), but checkout needs them. | Add a story to the Point of sale epic for Sprint 3, or let managers seed them through a Liquibase data file in v1. |
| Q2 | How many days before expiry counts as "close to expiry" (FR-INV-9)? | A store-wide setting, default 7 days. |
| Q3 | Buy-X-get-Y adds work to SCRUM-30, which was estimated at 3 points for percentage and flat discounts only. | Re-estimate SCRUM-30 with Jani; likely 5 points. |
| Q4 | Can a cashier cancel a completed sale, or is that a customer return (a stretch goal)? | Not in v1; a mistaken sale is handled as a manager adjustment later. |
| Q5 | Does `ADMIN_USERNAME` / `ADMIN_PASSWORD` need adding to `.env.example`? | Yes, as part of SCRUM-17. |
