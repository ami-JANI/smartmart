# SmartMart: An Integrated Super Shop Management System

A planned management system for a single supermarket branch. It covers point of sale, inventory, purchasing, user management, and reporting.

> **Status: planning stage.** This repository contains documentation only. No application code, build setup, or tests exist yet. Everything below describes agreed scope, not implemented features.

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

Detailed permission rules for each role have not been defined yet.

## Planned Modules

The modules are listed in priority order. None of them is implemented yet, and detailed requirements and acceptance criteria are still to be written.

| # | Module | Intended capabilities |
| --- | --- | --- |
| 1 | Point of sale | Barcode-based product selection, cart, checkout, discounts, payment recording, and invoice generation |
| 2 | Inventory | Product and category management, stock tracking, low-stock alerts, and batch and expiry-date monitoring |
| 3 | Purchasing | Supplier management, purchase orders, stock receiving, purchase records, and returns to suppliers |
| 4 | User management | Login and role-based access for the four roles above |
| 5 | Reporting | Sales, purchase, inventory, and profit summaries, plus a dashboard |

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

Alongside the application, the project is expected to produce the course's software engineering documents: requirements (SRS), UML and architectural design, estimation and scheduling, risk management, testing, and release documentation. None of these have been added to the repository yet.

## Repository Status

The repository contains only this README. The technology stack, architecture, database, and deployment approach have not been chosen, so there are no install, run, or test commands yet. Setup instructions will be added here when the codebase exists.

## Contributing

- Use GitHub issues to report problems or propose changes to the agreed scope. Discuss substantial changes with the team first.
- Create a focused branch for each change instead of committing directly to `main`.
- Open a pull request with a short explanation, and get a review from another team member before merging.

## License

This repository does not currently specify a license.
