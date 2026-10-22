# Frostlake scope decisions

Frostlake aims at Snowflake fidelity, so a Snowflake surface it refuses is normally a gap worth
closing. This file records the ones that are **deliberately** absent, and the ones whose support is
deliberately bounded, with the reason each decision rests on. A grammar or coverage sweep that
surfaces something listed here should read the entry rather than refile it as a gap.

Each entry says what was measured, on which account and when the wording matters, because a decision
made against an edition that lacks a feature is one to revisit when it becomes measurable.

## Indexes — not implemented, not recommended

Frostlake stores rows and scans them. There is no index structure anywhere in the engine and none is
planned: the project is a semantics emulator, not a storage engine, and every query feature is
expected to be correct without one. This is a standing architecture rule.

**SEARCH OPTIMIZATION is the boundary case, and it is in scope as METADATA ONLY.** Snowflake's
search access path is index-shaped, but the part users' SQL can observe is metadata: the DDL is
accepted, `SHOW TABLES` reports `search_optimization` / `search_optimization_progress` /
`search_optimization_bytes`, and `DESCRIBE SEARCH OPTIMIZATION ON <table>` lists the configured
expressions. Frostlake records and reports exactly that. Nothing in the executor learns about it —
no plan changes, no lookup structure, no measurable speed difference — so the rule above is intact.
Anything that would change how a query executes is out of scope.

## STORAGE LIFECYCLE POLICY — declined until it can be measured

Archival tiering has no meaning for an in-memory engine, and the surface cannot be pinned down on
the account Frostlake is verified against: `CREATE STORAGE LIFECYCLE POLICY … STORAGE_LIFECYCLE_ACTION(…)`
answers *Unknown function STORAGE_LIFECYCLE_ACTION*, so no policy can be created to attach, while
`SHOW STORAGE LIFECYCLE POLICIES` answers an empty result and `ALTER TABLE t ADD STORAGE LIFECYCLE
POLICY p` reports a syntax error at end of input — the clause takes more than a policy name, and
what more is not observable here. Emulating a shape from documentation alone is how invented
metadata gets in, so this waits until an account can answer for it.

## INTERVAL AS A DATA TYPE — a decision that was REVERSED; the type is implemented

This entry is kept because the decision it records was undone, and the reasoning behind it is quoted
in places this file does not reach. Nothing here is out of scope any more.

An interval is a value with its own types: `IntervalDayTimeType` and `IntervalYearMonthType`, each
carrying an `IntervalQualifier` — the unit at each end with its own precision (`DAY(9) TO SECOND(9)`,
`YEAR TO MONTH`). Two expressions produce one, and a bare interval projects:

    timestamp - timestamp        ->  INTERVAL DAY(9) TO SECOND(9)
    timestamp_ltz - timestamp_ntz
    SELECT INTERVAL '1' DAY      ->  INTERVAL DAY(9)

A column definition and a cast target spell the type themselves — `CREATE TABLE t (iv INTERVAL DAY(3) TO
SECOND(3))`, `'1 02'::INTERVAL DAY TO HOUR` — with the leading and fractional digits kept on the type, and a
text or an interval of the same family converts into it (a cast also reads an exact number as a count of a
one-field type's field, while a range of fields, and any column write, refuse a number); RESULT_SCAN refuses a result
holding one, except as a bare `SELECT *`, as the account does.

The in-string spelling is still refused standalone — `SELECT INTERVAL '1 day'` is *interval literal is
not supported in this form*, as on the account, where such an interval is only ever an OPERAND of
temporal arithmetic. `CAST('1 day' AS INTERVAL)` remains a syntax error.

What overturned the decision was the reason it rested on: that no client could receive an interval,
because the account's own JDBC driver refuses an interval column with *Feature unsupported: data type:
50006*. That
refusal holds only with JSON results, which is what the live-comparison harness forces. With the
driver's default ARROW results it reads one — a day-time interval as BigDecimal nanoseconds (a Duration
for SECOND), a year-month interval as a Period — so the values do cross a connection after all, and
Frostlake's driver reads them the same way. The wire form is in
[http-api.md](http-api.md#intervals).

What was true all along, and still is, is the arithmetic itself, which is the part users write: a
temporal shifted by an interval computes and TYPES correctly. With the quoted-string form the line falls at the
day rather than the month — a whole-day unit (year, month, day) leaves a DATE a DATE, while a sub-day unit (hour,
minute, second) promotes it to TIMESTAMP_NTZ, exactly as the account does; a unit-suffixed interval splits by family
instead — a year-month one keeps a DATE, a day-time one (`INTERVAL '1' DAY` included) makes it a TIMESTAMP_NTZ(9).

## HTTP AUTHENTICATION — none, by design; the server binds loopback and is exposed deliberately

The HTTP server authenticates nothing. `POST /api/execute`, `GET /api/health` and the `/api/sessions` endpoints ([http-api.md](http-api.md))
check no credential, token or origin, so anything that can reach the port can run arbitrary SQL —
including DDL and the file-reading COPY paths.

That is the intended shape for what Frostlake is: an engine you run beside your tests, holding data you
generated for those tests, for as long as they take. Authentication would add a wire contract to every
one of the seven drivers and the ODBC driver for a threat model that a local test engine does not have.

WHAT MAKES IT SAFE IS THE BIND, and it is already the default: `http.host` is `localhost`, so the server
listens on loopback and is unreachable from another machine unless someone changes it. Exposing it is a
deliberate act rather than an accident — set `http.host=0.0.0.0` and you have chosen to serve an
unauthenticated engine to your network.

TWO PLACES DO CHOOSE THAT, and both are documented where they are used rather than only here:
- the Docker image's entrypoint sets `http.host=0.0.0.0`, because a container must bind all interfaces
  to be reachable at all;
- `compose.yaml` publishes the port on the loopback interface (`127.0.0.1:18082:18082`) so the container
  default matches the engine default. Removing that prefix publishes it to every host interface.

If Frostlake ever needs to hold data worth protecting, this entry is the thing to revisit — not as a
missing feature, but as a change of what the project is for.

## INFORMATION_SCHEMA — answered, enumerated, and its views' columns listed

A database's INFORMATION_SCHEMA is a schema full of views, and Frostlake models all 62 of them as real
catalog objects: they are listed, they carry live's own metadata (owned by nobody, and the schema's fixed
comment), and the container-scoped listings tally exactly —

    SHOW VIEWS IN SCHEMA <db>.INFORMATION_SCHEMA   62      the account's own count
    SHOW VIEWS IN DATABASE <db>                    62 + your views
    SHOW OBJECTS IN DATABASE <db>                  62 + your tables and views
    SHOW TABLES IN DATABASE <db>                   your tables only — none of the 62 is a table

THEIR COLUMNS ARE THE ACCOUNT'S OWN, NOT INVENTED. The views' column lists are read from the account's own
description of them, `metadata/information-schema-columns.tsv` (what DESCRIBE VIEW answers there, in its
order), the file DESCRIBE reads too. `SHOW COLUMNS` lists them as `VIEW_COLUMN` rows with their descriptions,
and INFORMATION_SCHEMA.COLUMNS under TABLE_SCHEMA 'INFORMATION_SCHEMA', each with its type and nullability:
918 columns over the 63 views the file holds, as the account lists them.

    SHOW COLUMNS IN DATABASE <db>     the 918, and your tables' and views' columns

Sixteen of the views are genuinely answered — querying INFORMATION_SCHEMA.TABLES, .COLUMNS, .VIEWS,
.SCHEMATA and the rest returns real rows — and the others exist as names; their columns describe what they
would answer. Tests that walk a database or the account may count the 918 among a container's columns.

WHAT THE ANSWERED VIEWS LIST IS WHAT THE ROLE REACHES. Every role reads the views - they need no grant, and
none can be granted on one - and TABLES, COLUMNS, VIEWS, TABLE_CONSTRAINTS, REFERENTIAL_CONSTRAINTS, FUNCTIONS,
PROCEDURES, SEQUENCES, STAGES, FILE_FORMATS and SCHEMATA list only the objects the statement's rights own or
hold a privilege on, in the schemas and the database those rights were granted into. No role is exempt:
ACCOUNTADMIN does not see a table that another role owns and it was granted nothing on. Inside a stored
procedure that runs with owner's rights the rights are the owner's. The SHOW commands are not filtered this
way: they list every object, as the account's do.

## A Python handler catching a refused statement under a JavaScript procedure — a known limit with `-ea`

The JavaScript and the Python runtimes are separate GraalVM polyglot engines: JavaScript builds a context per
call on its own engine, and Python keeps one context per thread on another. When a Python handler runs under a
JavaScript procedure's `snowflake.execute` and CATCHES a statement the engine refused, the second and later such
calls fail on the polyglot runtime's own context-sharing check — but only where JVM assertions are enabled for
GraalVM's classes, as surefire's default `-ea` enables them in a test JVM. Without those assertions every such
call answers. Every other mix answers with them too: a Python handler that answers, raises, lets a refused
statement escape or catches a pure-Python exception, and a JavaScript handler under a Python one.

Evaluating both languages in one polyglot context, or a nested Python evaluation on a thread of its own with the
session's state carried over, would lift it; neither is done, since both reach deep into how a routine runs.

## Task notification and run properties — kept and shown, not acted on

A task's SUCCESS_INTEGRATION and ERROR_INTEGRATION are kept and shown, but no notification is sent: no message
service stands behind an integration here. OVERLAP_POLICY is kept and read back, but a task graph runs to its end
before the next run starts, so no run overlaps another. USER_TASK_TIMEOUT_MS is kept and shown and not enforced: a
task's statement runs to completion.

## Object kinds that are listed but not modelled — the listing answers its columns and no rows

A client that walks an account's metadata issues SHOW commands for kinds Frostlake has no object of: resource
monitors, shares, connections, applications and application packages, indexes, channels, snapshots, Git
repositories, semantic views, session, authentication, packages and privacy policies, external and data metric
functions, replication databases, replication and failover groups, replication accounts, and delegated
authorizations. None of these can be created here, and none is planned for its own sake: they are account
administration, sharing, replication and ingestion surfaces with nothing behind them in an in-memory engine.

Their listings are in scope all the same, **as shapes**: each `SHOW <KIND>` answers the columns a real account
declares for it — names, order and types — and no rows, and reads the scopes and modifiers the account's own
grammar reads for that kind (`executor/commands/UnmodelledListing`, `UnmodelledListings`). A scope is still
judged: a schema or a database that does not exist is refused as the account refuses it, kind by kind. So
metadata tooling runs unchanged, and a refusal still means the statement is wrong rather than unsupported.

Three listings of the same family do answer rows, because the engine has something to say: `SHOW EXPORTED KEYS`
(the foreign keys that reference a table, read from the referenced side), `SHOW GRANTS ON ACCOUNT` (the
privileges granted on the account in this engine — the grants an account's built-in roles hold from the start
are not seeded) and `SHOW REGIONS` (the public list of regions, transcribed from an account; any scope is
refused, as there, and `IN DATA EXCHANGE` is not read).

Adding CREATE for one of these kinds is a separate decision, to be made when a use case needs the object rather
than its listing.

## Related

- [functions.md](functions.md) — the catalog of supported functions, including the function families
  that are out of scope and why.
- [operators.md](operators.md) — the operator counterpart.
