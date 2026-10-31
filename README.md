# SmartMart: An Integrated Super Shop Management System

A planned single-branch supermarket management system for point of sale, inventory, purchasing, user management, and reporting.

**Status: planning stage.** This repository currently contains project documentation only. The capabilities below describe the agreed development scope; they are not implemented features.

## Overview

Supermarket operations require sales, stock, supplier purchases, and staff access to stay consistent. SmartMart aims to bring these workflows together so a shop can reduce manual errors, maintain accurate stock records, and review its daily operations.

The intended users are shop administrators, managers, cashiers, and inventory staff. The project takes inspiration from supermarket operations in Bangladesh, including shops such as Shwapno, Agora, and Unimart; it has no stated affiliation with those businesses.

SmartMart is a three-person academic project for **CSE 3219: Software Engineering and Project Management** and **CSE 3220: Software Engineering and Project Management Laboratory**, with a planned development period of **10 weeks**. Its initial scope is one shop branch.

## Planned Scope

All five modules are planned. Detailed requirements and acceptance criteria still need to be defined.

| Module | Intended capabilities |
| --- | --- |
| Point of sale | Barcode-based product selection, a shopping cart, checkout, discounts, payment recording, and invoice generation. |
| Inventory | Product and category management, stock tracking, low-stock alerts, and batch and expiry-date monitoring. |
| Purchasing | Supplier management, purchase orders, stock receiving, and purchase records. |
| User management | Login and role-based access for administrators, managers, cashiers, and inventory staff. |
| Reporting | Sales, purchase, inventory, and profit-related summaries and dashboards. |

Payment recording does not imply an implemented payment gateway or live payment processing. Barcode workflows and any required scanner hardware have not yet been specified.

## Intended Workflow

The proposed workflow connects the core modules:

1. Authorized staff maintain products, categories, and supplier records.
2. Staff record received purchases to increase available stock.
3. Cashiers select products and complete checkout, producing an invoice and reducing stock.
4. Managers review stock levels, expiry information, and operational reports.

These steps describe the target behavior. There is no runnable application in this repository yet.

## Development Priorities

The team has prioritized point of sale, inventory, purchasing, user management, and reporting within the 10-week development window. A detailed weekly schedule, start date, milestones, and individual responsibilities have not been finalized.

Course work associated with the project is expected to cover requirements and SRS documentation, UML and architectural design, project estimation and scheduling, risk management, testing, and release documentation. These artifacts have not yet been added.

## Getting Started

No application installation or configuration is required to read the current documentation. To obtain a local copy, use Git:

```sh
git clone https://github.com/ami-JANI/smartmart.git
cd smartmart
```

For a private repository, authenticate with a GitHub account that has access.

The technology stack, architecture, runtime requirements, database, and deployment approach have not yet been selected. There are no dependency manifests, environment templates, database migrations, application startup commands, or local service URLs to document.

## Verification and Testing

There is no application code, automated test suite, build configuration, or CI workflow at this stage. Review the documentation against the agreed scope; functional verification will become possible after implementation begins.

## Current Limitations

- The repository is a planning starting point and cannot perform shop operations.
- Authentication, authorization, stock updates, reports, and other planned behavior are not implemented.
- Detailed permission rules, discount and payment rules, and profit calculations remain undefined.
- Multi-branch operation is outside the initial scope. Loyalty features, returns, forecasting, and other extensions are not committed deliverables.
- No demo, screenshots, deployment, or production-readiness claims are available.

## Contributing

Use GitHub issues to report documentation problems or propose changes to the agreed scope. Discuss substantial changes with the team before implementation.

Create a focused branch, make the change, check that the documentation accurately reflects the current project, and open a pull request with a short explanation. When build and test commands are introduced, document them here and run the relevant checks before submitting code changes.

## License

This repository does not currently specify a license.
