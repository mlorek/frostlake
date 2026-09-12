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

## INTERVAL AS A DATA TYPE — declined; the type has no values that cross a connection

Two expressions Snowflake accepts produce an interval, and Frostlake declares its VARCHAR placeholder
for both:

    timestamp - timestamp   ->  INTERVAL DAY(9) TO SECOND(9)
    timestamp_ltz - timestamp_ntz

Adding INTERVAL to the `DataType` hierarchy was considered and declined, because measuring the account
showed how little of it is reachable:

- A bare interval is NOT a value. `SELECT INTERVAL '1 day'` is refused — *interval literal is not
  supported in this form* — in every spelling probed, and Frostlake already answers that same sentence.
  An interval is only ever an OPERAND of temporal arithmetic.
- `SELECT ts - ts` cannot be fetched at all. The account's own JDBC driver refuses to return the value:
  *Feature unsupported: data type: 50006*. The type exists inside the engine, but no client of the kind
  Frostlake serves can receive one.
- `CAST('1 day' AS INTERVAL)` is a syntax error.

So the whole observable surface of INTERVAL is two metadata strings: what `SYSTEM$TYPEOF` prints, and
the `data_type` descriptor a view declares for such a column. A full data type — grammar, canonical
spelling, the JDBC type mapping, the wire form, and INTERVAL's own qualifier syntax with a precision on
each end (`DAY(9) TO SECOND(9)`, `YEAR TO MONTH`) — is a large surface to build for two strings
describing values that cannot be selected.

What IS supported is the arithmetic itself, which is the part users write: a temporal shifted by an
interval computes and TYPES correctly, and the line falls at the day rather than the month — a whole-day
unit (year, month, day) leaves a DATE a DATE, while a sub-day unit (hour, minute, second) promotes it to
TIMESTAMP_NTZ, exactly as the account does.

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

## INFORMATION_SCHEMA — answered and enumerated, but its views' COLUMNS are not

A database's INFORMATION_SCHEMA is a schema full of views, and Frostlake models all 62 of them as real
catalog objects: they are listed, they carry live's own metadata (owned by nobody, and the schema's fixed
comment), and the container-scoped listings tally exactly —

    SHOW VIEWS IN SCHEMA <db>.INFORMATION_SCHEMA   62      the account's own count
    SHOW VIEWS IN DATABASE <db>                    62 + your views
    SHOW OBJECTS IN DATABASE <db>                  62 + your tables and views
    SHOW TABLES IN DATABASE <db>                   your tables only — none of the 62 is a table

WHAT IS NOT MODELLED IS THEIR COLUMNS. The 62 are registered as views without a resolved column list, so
a container-scoped `SHOW COLUMNS` counts user objects only:

    SHOW COLUMNS IN DATABASE <db>     live 894 over one table and one view; Frostlake 3

Sixteen of the views are genuinely answered — querying INFORMATION_SCHEMA.TABLES, .COLUMNS, .VIEWS,
.SCHEMATA and the rest returns real rows — and the other 46 exist as names. Giving all 62 a column list
would mean INVENTING the columns of the 46 Frostlake does not implement, which is the one thing this
project will not do with metadata: a listing that is short is honest, a listing padded with made-up
columns is not. The 16 could be populated from what they already answer, but a partly-populated listing
is harder to reason about than an absent one, because nothing tells the reader which half they have.

So container-scoped `SHOW COLUMNS` counts user objects, and tests that walk a database or the account
assert "at least mine" rather than a total. Revisit only if something real depends on the count.

## Related

- [functions.md](functions.md) — the catalog of supported functions, including the function families
  that are out of scope and why.
- [operators.md](operators.md) — the operator counterpart.
