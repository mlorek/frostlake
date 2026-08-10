# Changelog

Notable changes to Frostlake, newest first. Versions follow the published Maven artifacts
(`dev.frostlake:frostlake-db` and the six optional packs, which release together).

Frostlake's goal is to answer SQL the way a real Snowflake account does. That makes **a new refusal a
fix, not a regression**: SQL that Snowflake rejects and Frostlake accepted was a fidelity bug, and
closing it can break code that relied on the leniency. Those changes are listed first in every release
for exactly that reason.

## 0.0.7 — unreleased

Every behavioural claim below was measured against a live Snowflake account, and is covered by a test
that runs against both Frostlake and that account.

### Now refused (SQL that earlier versions accepted)

- **A reference that does not resolve, wherever it is written.** `ORDER BY`, `GROUP BY`, `HAVING`,
  `QUALIFY`, window partition and order keys, and `WHERE` all raise `invalid identifier` for a name
  that names nothing, at the reference's own position. A qualified reference is checked in full — a
  wrong schema part or a three-part column path no longer resolves by accident.
- **A quoted identifier resolves exactly.** `"a"` used to fold and find column `A`; it now matches its
  own spelling only, in every path — the select list, qualified references, `ORDER BY` keys, quoted
  aliases. The refusal echoes the name as written, keeping the quotes only when the name could not be
  written without them (`"Q"` echoes as `Q`, `"qQ"` keeps its quotes).
- **Misplaced aggregates and window functions.** A window function inside `HAVING` or a `GROUP BY` key,
  an aggregate inside `WHERE`, and a window inside an aggregate are each refused with the account's own
  misplacement sentence. An aggregate in `ORDER BY` or `QUALIFY` now makes the query an aggregate
  query, so an ungrouped select list beside it is refused rather than silently grouped.
- **Conditional branches that cannot fold.** `IFF` / `CASE` / `COALESCE` and family over two type
  families with no common supertype — a `DATE` beside a `TIME`, a `BINARY` beside a `VARCHAR` — are
  compile-time errors, as is a `BINARY` beside a string anywhere in the `CONCAT` / `IFF` family.
- **Concatenating a container with anything.** `OBJECT`, `ARRAY`, `GEOGRAPHY` and `GEOMETRY` take no part
  in `||` — not against a string, a number, a `VARIANT`, or each other. `VARIANT` is unaffected and still
  concatenates to full-width text.
- **Eight temporal arithmetic shapes.** Of the 32 combinations of `DATE` / `TIME` / `TIMESTAMP` / `NUMBER`
  under `+` and `-`, only seven are legal. Note the asymmetry: `NUMBER + DATE` is accepted but
  `NUMBER - DATE` is refused, a `TIMESTAMP` takes no numeric operand on either side, and `TIME` is refused
  against everything including itself.
- **An unknown function name, at compile time.** Previously this was raised only when a row was evaluated,
  so the same statement was accepted or refused depending on whether the table happened to have rows — and
  a `CREATE VIEW` or `CREATE TABLE AS` over an empty table was accepted with an uncompilable body.
- **Decoded bytes that are not text.** `BASE64_DECODE_STRING` and `HEX_DECODE_STRING` refuse input whose
  bytes are not valid UTF-8 (`Invalid UTF8 detected while decoding 'qw=='`) instead of substituting
  replacement characters; the `TRY_` spellings answer `NULL`. `JSON_EXTRACT_PATH_TEXT` refuses a
  statically `OBJECT`-typed first argument, and `TRY_VALIDATE_UTF8` refuses a `BINARY` — both at compile
  time, naming the offending types.
- **`OBJECT_CONSTRUCT` with a `VARIANT` key.**
- **`ALTER TABLE … ADD COLUMN` default values** that do not match the column's type family, and computed
  defaults generally: `ADD COLUMN` takes a bare literal where `CREATE TABLE` accepts an expression.
- **A `BINARY` column with any `DEFAULT`**, and several other DDL shapes a real account rejects
  (duplicate `COMMENT`, transient clone, `COLLATE` after `DEFAULT`, sub-9 precision defaults).

### Changed

- **Result columns now declare computed types instead of placeholders.** Conditionals fold their
  branches by the account's rules (`FLOAT` beats `NUMBER` from either side, `DATE` widens into the
  timestamps, a `NULL` branch contributes nothing); string functions compute a width from their input
  instead of claiming 16MB; each aggregate declares its own type (`COUNT` is `NUMBER(18,0)`, not
  `BIGINT(38,0)`); computed columns like `i + 1` and `LENGTH(t)` are typed; `DATEADD` over a `DATE`
  stays a `DATE`; every integer alias reports `NUMBER(38,0)`; `VARBINARY` reports `BINARY` and bare
  `TIMESTAMP` reports the session's `TIMESTAMP_NTZ`; a set operation declares the type its arms fold
  to, not the leading arm's. A `FLOAT` reports no precision or scale anywhere — including
  `ResultSetMetaData`, which answered 38/9 before.
- **Error precedence and positions match the account.** An unknown function outranks an argument-type
  error; a leading comment no longer shifts an error's position; a dozen syntax-error shapes anchor
  where live anchors (`SELECT case FROM t` points at the next token, a truncated statement runs to end
  of input); a refused window call is echoed canonicalised, and `SELECT constraint` is an invalid
  identifier rather than a syntax error.
- **The unknown-function message** is now `SQL compilation error:\nUnknown function X.` (previously
  `Unknown function: X`), and a qualified name reads `Unknown user-defined function X.`. Code matching on
  the old text needs updating — a matcher inside this repository did.
- **Argument-type refusals now carry a position**, anchored on the operator token, as a real account's do:
  `error line 1 at position 10 Invalid argument types for function '||': (BINARY(4), VARCHAR(4))`.
- **`SHOW COLUMNS` reports `data_type` as Snowflake's JSON descriptor** rather than a type name, and its
  relation-kind keyword is no longer a filter — `TABLE`, `VIEW` and the bare form each resolve a table, a
  view or a materialized view alike. The unnamed listing covers all three kinds, `IN SCHEMA db.schema`
  is accepted, and unset cells are spelled empty rather than `NULL`.
- **`DATE + INTERVAL '1 day'` stays a `DATE`.** The promote-to-timestamp line falls at the day, not the
  month. The two interval spellings genuinely differ for `DAY`: `INTERVAL '5 days'` keeps a `DATE`,
  `INTERVAL '5' DAY` promotes to `TIMESTAMP_NTZ`.
- **Column nullability crosses the wire and the JDBC surface**, so `ResultSetMetaData.isNullable` answers
  from the column rather than guessing.

### Fixed

- **A window ranking over grouped rows ranked every group equal.** `RANK() OVER (ORDER BY SUM(b))` on a
  grouped query returned `1, 1` instead of `1, 2`: a window over grouped rows now evaluates raw
  aggregates over each row's own source group, whether or not the aggregate is also a select item.
- **An exponent literal without a decimal point.** `1e0`, `1E+3`, `1e-3` were syntax errors in every
  argument or clause position — and in a select list `SELECT 1e0` silently parsed as `SELECT 1 AS e0`,
  returning the wrong column name. They now read as fixed-point numbers everywhere (`1e0` is
  `NUMBER(1,0)`, `1e20` prints `100000000000000000000`).
- **Names the account accepts are accepted.** `auto`, `break`, `continue` and a handful of other words
  now work as column, table, alias and CTE names; `POSITION(x IN y)` parses; `ORDER BY` may sort by a
  window call; `GREATEST` / `LEAST` fold a `DATE` beside a `TIMESTAMP` and answer instead of refusing;
  `INTERVAL` accepts `WEEK` and `QUARTER`.
- **A failed `CREATE OR REPLACE VIEW` no longer destroys the existing view.** Every failure mode —
  unnamable column, uncompilable body, wrong-count column list, missing base table, invalid identifier —
  used to drop the old view before validating the new one. The materialized-view path had the same bug.
- **View and CTAS bodies are validated before the object is created**, and the checks now run in the
  account's order: the body compiles first, and its failure is reported ahead of any column-naming or
  column-count complaint. A body refused for an argument type can no longer produce a view with no
  columns.
- **`GREATEST` over a binary and a string** refused with the account's sentence instead of throwing a
  raw `ClassCastException`.
- Numeric literals carry their own precision and scale, so a value like `0.05` is no longer stored as `0`
  through a derived table.
- `INFORMATION_SCHEMA` relations are listable, so container-wide listings no longer undercount.
- A comma join with its equality in `WHERE` now plans like the `ON` spelling (was ~800× slower), and
  large joins hash instead of scanning.

### Added

- `frostlake-ai`, `frostlake-geo`, `frostlake-rt-js`, `frostlake-rt-py` and `frostlake-rt-scala` publish
  alongside `frostlake-db` and `frostlake-formats` — **seven artifacts**, so the engine stays lean and each
  optional pack is opt-in by classpath.
- A JDBC `file:` transport (`jdbc:frostlake:file:<dir>`) — embedded and persistent, WAL-durable by default.
- The DML `DEFAULT` marker: `INSERT … VALUES (DEFAULT)` and `UPDATE … SET x = DEFAULT`.
- Structured `OBJECT` / `ARRAY` / `MAP` type spellings in metadata surfaces.

### Internal

- The codebase now has no lambdas, no streams and no static nested classes, enforced by Checkstyle at
  error severity in CI along with `final` on every never-reassigned local and one statement per line.
- A CI check refuses a build whose downstream projects pin a stale engine version.

## 0.0.6 and earlier

No changelog was kept before 0.0.7. The git history since `v0.0.3` is the record.
