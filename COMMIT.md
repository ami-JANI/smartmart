# Commit Message Rules

Every commit in this repository must follow the [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) specification. This applies to every team member and every commit, including documentation-only changes.

The rules are checked automatically in two places:

- **Locally**, by the `commit-msg` hook in `.githooks/`, once you enable it (see [One-time setup](#one-time-setup)).
- **On GitHub**, by the `Commit messages` workflow, which checks every commit pushed to `main` and every commit in a pull request.

## One-time setup

Run these commands once, from the repository root, after you clone:

```sh
git config core.hooksPath .githooks
git config commit.template .gitmessage
```

The first command turns on the local check, so Git rejects a bad message before the commit is created. The second command pre-fills your editor with a reminder of the format when you run `git commit` without `-m`.

On macOS or Linux, also make sure the hook is executable:

```sh
chmod +x .githooks/commit-msg
```

## Message structure

```
<type>[optional scope][!]: <description>

[optional body]

[optional footer(s)]
```

1. The first line is the **header**. It starts with a type, an optional scope in parentheses, an optional `!`, then a colon and one space, then a short description.
2. If there is a body, it starts after **one blank line** below the header. It can have several paragraphs.
3. If there are footers, they start after **one blank line** below the body (or the header, if there is no body).

## Types

| Type | Use it for | Version effect |
| --- | --- | --- |
| `feat` | A new feature for users | MINOR |
| `fix` | A bug fix | PATCH |
| `docs` | Documentation only (README, SRS, UML, reports) | none |
| `style` | Formatting, whitespace, semicolons; no change in behaviour | none |
| `refactor` | Code change that neither fixes a bug nor adds a feature | none |
| `perf` | Code change that improves performance | none |
| `test` | Adding or correcting tests | none |
| `build` | Build system or dependencies (Maven, npm, Docker) | none |
| `ci` | CI configuration (GitHub Actions workflows) | none |
| `chore` | Other maintenance that does not touch source or tests | none |
| `revert` | Reverts an earlier commit | depends |

`feat` and `fix` are defined by the specification. The other types follow the widely used `@commitlint/config-conventional` list. Only the types in this table are accepted here. Write types in lowercase.

## Scopes

A scope is an optional noun in parentheses that names the part of SmartMart the commit touches. Use one of these:

| Scope | Area |
| --- | --- |
| `pos` | Point of sale: cart, checkout, discounts, payments, invoices |
| `inventory` | Products, categories, stock, low-stock alerts, batches and expiry |
| `purchasing` | Suppliers, purchase orders, receiving, supplier returns |
| `auth` | Login, users, roles and permissions |
| `reports` | Sales, purchase, inventory and profit reports, dashboard |
| `db` | Database schema and migrations |
| `ui` | Shared frontend layout and components |
| `api` | Shared backend API concerns |
| `srs` | Software requirements specification |
| `uml` | UML and design diagrams |
| `plan` | WBS, PERT/CPM, Gantt, estimation, risk register |
| `deps` | Dependency updates |
| `repo` | Repository setup, hooks, templates |

Scopes are lowercase letters, digits and hyphens. Leave the scope out when a change spans many areas. If you need a new scope, add it to this table in the same commit.

## Description

- Use the imperative mood: "add", "fix", "remove", not "added" or "fixes".
- Start with a lowercase letter and do not end with a period.
- Keep the whole header at **72 characters or fewer**.
- Say what the commit does, not how you got there.

## Body

- Explain **what** changed and **why**, not how.
- Wrap lines at about 72 characters.
- Separate paragraphs with a blank line.

## Breaking changes

A breaking change is anything that forces other code, data or users to change: a renamed API route, a removed field, a changed database column, a new required login role. It triggers a MAJOR version bump and can be used with any type.

Mark it in one or both of these ways:

1. Add `!` right before the colon: `feat(api)!: rename /items to /products`.
2. Add a footer that starts with `BREAKING CHANGE:` (uppercase) and explains the change and what others must do.

```
feat(auth)!: require a role on every API route

BREAKING CHANGE: requests without a role claim now return 403.
Every user account must be assigned one of admin, manager,
cashier or inventory-staff before upgrading.
```

`BREAKING-CHANGE:` is accepted as a synonym in footers.

## Footers

Footers use the Git trailer format: a token, then `: ` or ` #`, then a value. Tokens use hyphens instead of spaces, except `BREAKING CHANGE`.

```
Refs: #12
Closes: #34
Reviewed-by: Rahim
```

Reference GitHub issues in footers whenever a commit relates to one.

## One change per commit

If a change fits more than one type, split it into separate commits. For example, a new feature and its unrelated bug fix go in two commits.

## Examples

```
feat(pos): add barcode lookup to cart
```

```
fix(inventory): stop low-stock alert firing at zero threshold
```

```
feat(purchasing): create purchase orders from supplier list

Inventory staff can now pick a supplier and add products to a draft
purchase order. Prices default to the supplier's last quoted price.

Refs: #18
```

```
docs(srs): add use case diagrams for checkout
```

```
test(reports): cover daily sales totals with discounts
```

```
refactor(pos): extract discount rules into strategy classes
```

```
feat(db)!: store prices in paisa instead of taka

BREAKING CHANGE: the price columns in products and sale_items are now
integers in paisa. Run migration V5 before starting the new build.
```

```
revert: let cashiers edit invoice totals

Refs: 676104e
```

## Messages that are rejected

| Message | Problem |
| --- | --- |
| `Added barcode lookup` | No type |
| `feature(pos): add barcode lookup` | `feature` is not an accepted type |
| `feat(POS): add barcode lookup` | Scope must be lowercase |
| `feat(pos):add barcode lookup` | Missing space after the colon |
| `feat(pos): ` | Empty description |
| `fix: update stuff` followed directly by body text | Body must start after a blank line |

## Exceptions

The checks skip these automatically generated messages:

- Merge commits created by Git or GitHub (`Merge branch ...`, `Merge pull request ...`).
- `fixup!` and `squash!` commits made by `git commit --fixup`, which must be squashed before pushing.

## Fixing a bad message

- **Last commit, not pushed yet:** `git commit --amend`
- **Older commits, not pushed yet:** `git rebase -i HEAD~N`, then mark the commits as `reword`.
- **Already pushed to `main`:** do not rewrite shared history. Tell the team, and follow the rules from the next commit on.
