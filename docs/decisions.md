# Design Decisions

Design decisions about the SmartMart system, newest first. Each entry gives the date, the decision, and why (GUIDELINES.md section 10).

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
