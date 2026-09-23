# HTTP API

`dev.frostlake.http.DatabaseHttpServer` (`data/start-http-server.sh [PORT] [-f init.sql]`, default port
18082) serves the engine as JSON over HTTP. Every Frostlake driver speaks this protocol, and so does the JDBC
driver's `jdbc:frostlake://host:port/db` transport. Nothing is authenticated — see [scope.md](scope.md).

The same server also answers Snowflake's REST APIs for resource management under `/api/v2` — see
[rest-api.md](rest-api.md).

## `POST /api/execute`

Request:

```json
{"sql": "SELECT 1", "sessionId": "…", "requireSession": true, "autoCommit": true}
```

| field | meaning |
|---|---|
| `sql` | one statement, or several separated by semicolons |
| `sessionId` | optional: the session to run in (see *Sessions*) |
| `requireSession` | optional, default `true`: a `sessionId` naming no live session is refused (404) and nothing runs. `false` starts a fresh session under that id instead (see *Sessions*) |
| `multiStatementCount` | optional: how many statements this request holds. The account's driver sends the count with the statement, and the server refuses a request whose statement count differs. `0` means any number. A request that omits the field inherits the session's `MULTI_STATEMENT_COUNT`, which starts at 1. |
| `autoCommit` | optional: the client's autocommit mode. The session's `AUTOCOMMIT` setting follows it whenever it changes — the first value a session receives, and each later value that differs from the one before it — and the statement then runs under it, as it would after `ALTER SESSION SET AUTOCOMMIT`. A request that omits the field, or repeats the value it last sent, leaves the setting as it stands, so an `ALTER SESSION SET AUTOCOMMIT` run in between stays in force. |

Response:

```json
{"success": true, "sessionId": "…", "newSession": false, "errorMessage": null,
 "executionTimeMs": 3, "resultSets": [ … ]}
```

| status | when |
|---|---|
| 200 | the statement ran — or FAILED: `success` says which. A failed statement carries `errorMessage` and the `sessionId` it ran in; the session and its context survive the failure. |
| 400 | the body has no `sql` field: nothing ran, and the answer is the usual failure — `success: false`, `errorMessage: "SQL is required"`, `sessionId: null` |
| 404 | `sessionId` names no live session and the request did not set `requireSession: false`: nothing ran, and `sessionId` is null |
| 405 | any method but POST |
| 500 | the server could not handle the request at all (an unreadable body, an internal fault such as a class it cannot load). A fault is always answered, never left to the client's timeout. |

Decide success from the body's `success`: a SQL error is an answer, not a transport failure.

A `sql` that holds no statement — empty, blank, a lone `;` or only comments — is a statement like any other and
fails as the account fails it: 200 with `success: false` and `SQL compilation error:` / `Empty SQL statement.`, in a session.

Several statements in one request are **refused unless the caller asks for them**, as the account refuses them:
`Actual statement count 2 did not match the desired statement count 1.` Ask with `multiStatementCount` on the
request, or with `ALTER SESSION SET MULTI_STATEMENT_COUNT = n` for the session; `0` allows any number. The count
is matched in both directions, so declaring 2 and sending 1 is refused too. What the account does not count does
not count here either: a trailing `;`, an empty statement, a `;` inside a string, a quoted identifier, a comment
or a `$$…$$` body, and a scripting block counts as one statement. The text is compiled before it is
counted, as the account compiles it: a text that will not parse is refused with its syntax error whatever its
count, in whichever statement the fault lies, while a fault found only when a statement runs leaves the count
refusal to answer.

The text of an `EXECUTE IMMEDIATE` is counted the same way, before any of it runs: against the request's
`multiStatementCount` when the request sends one, else against the session's `MULTI_STATEMENT_COUNT` — and
always against the session's inside a scripting block. When a text of several statements runs, a request that
is that `EXECUTE IMMEDIATE` alone answers one `resultSets` entry per statement of the text, in order; the same
`EXECUTE IMMEDIATE` as one statement of a request of several answers a single row, `multiple statement
execution` = `Multiple statements executed successfully.`, in its place. An `EXECUTE IMMEDIATE FROM` a staged file is
not counted, and answers with its last statement's result alone.

When one statement of such a request fails, the answer **names the statement that failed**, as the account names
it: its own text, the line it starts on, and its column within that line — counted from zero — with the
statement's own error carried inside.

```
JavaScript execution error: Uncaught Execution of multiple statements failed on statement
"SELECT * FROM nowhere" (at line 1, position 52).
SQL compilation error:
Object 'NOWHERE' does not exist or not authorized. in SYSTEM$MULTISTMT at …
```

There are **no partial result sets**: `resultSets` is empty however far the script got, which is what the account
answers too — a client cannot read the results of the statements that did run. A statement sent on its own fails
with its own error and nothing around it, and a script whose text will not parse is refused as a whole before any
of it runs, with an ordinary syntax error naming no statement.

### Result sets

```json
{"columns": [{"name": "I", "dataType": "NUMBER", "precision": 38, "scale": 0, "nullable": true},
             {"name": "S", "dataType": "VARCHAR", "precision": 0, "scale": 0, "nullable": true, "length": 9},
             {"name": "T", "dataType": "TIMESTAMP_NTZ", "precision": 0, "scale": 3, "nullable": true}],
 "rows": [[1, "a", "2024-01-01 00:00:00.000"], [2, "b", null]], "rowCount": 2, "updateCount": -1,
 "jdbcUpdateCount": -1}
```

`precision` and `scale` describe a fixed-point NUMBER. A TIME or TIMESTAMP column — TIMESTAMP_NTZ,
TIMESTAMP_LTZ and TIMESTAMP_TZ alike — sends `precision` 0 and its fractional-second precision as `scale`, as
the account's own result metadata does, and Snowflake's drivers report that number as the column's scale:
`TIME(3)` sends 3, `TIMESTAMP_NTZ(0)` 0, a bare `TIMESTAMP_LTZ` and `CURRENT_TIMESTAMP` 9, `CURRENT_TIMESTAMP(3)`
3, and a SHOW command's `created_on` 3. A server that predates these digits sends 0 for every time and timestamp
column. Every other type sends 0 for both, DATE included, except an interval (below). `length` is sent for
a text (VARCHAR) or binary (BINARY) column only: its length in characters or bytes, the number Snowflake's own
driver reports as the column's precision and display size. `VARCHAR(9)` sends 9, a table's bare `VARCHAR`
16777216, a cast to bare `VARCHAR` 134217728, a table's bare `BINARY` 8388608, and a binary the plan never sized
— `TO_BINARY(…)`, a cast to bare `BINARY` — 67108864. Every other type omits the field, as does a server that
predates it.

`updateCount` is the affected-row count when the result is a DML statement's count grid (INSERT, UPDATE,
DELETE, MERGE) and `-1` for every other result — queries, DDL status lines, SHOW. A DML statement still answers
its Snowflake-shaped grid (`number of rows inserted`, …); `updateCount` is what says it IS one, so a query that
names its columns the same way is never taken for DML. A result without the field comes from a server that
predates it.

`jdbcUpdateCount` is what a JDBC client reports for the statement, as Snowflake's JDBC driver does: `-1` when the
statement answers rows to read — a query, SHOW, DESCRIBE, EXPLAIN, CALL, LIST, REMOVE, GET, PUT, an anonymous
block — and otherwise the update count `execute()` answers `false` for: the affected rows of an INSERT (one
target or several), UPDATE, DELETE or MERGE, every count of its grid added up; the rows a COPY INTO a table
loaded; and `0` for DDL, USE, SET, a transaction statement, an unload and every other statement. An EXECUTE
IMMEDIATE reports what the statement its text ran reports; for a text of several statements, each entry reports its
own statement's count, and the one row that stands for the text among other statements is rows to read (`-1`). The grid is sent either way — the JDBC driver hands
it back from `executeQuery` of a single statement — so a client that shows the status line and the count grid
can go on doing so. Always sent; absent means a server that predates the field, and the driver then treats every
result as rows, as it used to.

Cells cross as JSON values: numbers as numbers, BOOLEAN as a JSON boolean, VARIANT / OBJECT / ARRAY as their
JSON text, BINARY as upper-case hex text, VECTOR as its text (`[1.000000,2.000000,3.000000]`, an INT
vector `[1,2,3]`), and temporal values as text:

| type | wire text |
|---|---|
| DATE | `2024-01-01` |
| TIME | `12:34:56`, plus `.fff`, `.ffffff` or `.fffffffff` when the value has a fraction |
| TIMESTAMP_NTZ | `2024-01-01 12:34:56.fff`, `.ffffff` or `.fffffffff` — as many fractional digits as the value needs, never fewer than three |
| TIMESTAMP_LTZ, TIMESTAMP_TZ | the same, followed by ` ±HHMM` |

The wire keeps the whole fraction of a second. The account's drivers DISPLAY three digits for a timestamp and
none for a TIME, so a client rendering display text cuts the fraction back (the JDBC driver's `getString` does).

#### Intervals

An interval column — a TIMESTAMP difference, a projected unit-suffixed literal such as `INTERVAL '1' DAY` —
crosses under the family name Snowflake's own JDBC driver reports, with precision 0 and a `scale` that says
which fields the interval spans:

| `dataType` | `scale` | declared type |
|---|---|---|
| `INTERVAL_DAY_TIME` | 3 | `INTERVAL DAY(9) TO SECOND(9)` (a TIMESTAMP difference) |
| `INTERVAL_DAY_TIME` | 6 / 9 / 11 / 12 | `INTERVAL DAY(9)` / `HOUR(9)` / `MINUTE(9)` / `SECOND(9,9)` |
| `INTERVAL_YEAR_MONTH` | 1 / 2 | `INTERVAL YEAR(9)` / `MONTH(9)` |

(The driver's full code table, the compound qualifiers included, is `IntervalQualifier` in the engine.) Each
cell is a JSON **string** of plain decimal digits: a day-time interval's signed count of NANOSECONDS, a
year-month interval's signed count of MONTHS — `INTERVAL '1' DAY` is `"86400000000000"`, a timestamp difference
of a day and an hour `"90000000000000"`, `INTERVAL '1' YEAR` `"12"`. These are the numbers the account's own
JSON result rows carry for the type; a string keeps a count past 2<sup>53</sup> exact for clients whose JSON
numbers are doubles.

A client should read an interval column the way Snowflake's JDBC driver does with its default (ARROW)
results. What a cell becomes follows the column's storage width: a day-time interval stored in sixteen bytes
(DAY, HOUR, MINUTE, a timestamp difference — at their default nine leading digits, every day-time code but 12)
is a fixed-point count of nanoseconds, so `getString` is the digits and `getObject` a `BigDecimal`; an
`INTERVAL SECOND(9,9)` (code 12, eight bytes) is a `java.time.Duration` (`getString` `PT1S`); and a year-month
interval is a `java.time.Period` of its months, normalized (`getString` `P1Y2M` for fourteen). A day-time column
declared with few enough leading digits for its span to fit eight bytes (DAY(5), HOUR(6), MINUTE(8) or fewer) is
narrower than its code says — an `INTERVAL DAY(2)` is stored in eight bytes and read as a `Duration` (`PT24H` for a
day), while an `INTERVAL DAY(6)` is still sixteen. The wire carries no interval precision, so this project's
JDBC driver reads such a column by its declared width only in-process (`jdbc:frostlake:direct:` and
`jdbc:frostlake:file:`); over HTTP it reads every day-time code but 12 as the sixteen-byte kind. Only the
sixteen-byte kind answers the numeric getters (`getLong` and the narrower ones while the count fits,
`getBoolean` for 0 and 1); the others refuse them with
`Cannot convert value in the driver from type:INTERVAL_DAY_TIME to type:long, value=.` (SQLSTATE `0A000`, code
200038), naming `INTERVAL_YEAR_MONTH` for a year-month column and the getter's own type in place of `long`
(`to type:int, value={2}.` for `getInt`). `getColumnClassName` refuses every interval column (`No corresponding Java type is found for
java.sql.Type: 50006`); the type codes are 50006 and 50005. A client with no interval type of its own may
simply hand the digits back.

## Sessions

A session holds the current database and schema, session variables, `ALTER SESSION` settings and an open
transaction. It lives until it is released or has been idle for 30 minutes.

- A request without `sessionId` runs in a new session; the response's `sessionId` names it, and `newSession` is
  `true`.
- A request whose `sessionId` names a live session runs in it (`newSession: false`).
- A request whose `sessionId` names no live session — idle-expired, released, or from before a server restart —
  is refused (404), and nothing runs: the context the client set up earlier is gone, and running the statement
  in a fresh session would put it somewhere the client never chose. The client drops the id and starts over:
  a request without `sessionId` gets a new session, where it re-establishes its scope.
- A request that sets `requireSession: false` asks for the old lenient answer instead: it runs in a FRESH
  session started under that id at the server's default database and schema, and answers `newSession: true`.
- `newSession` is on every answer that names a session, so its presence tells a client the server understands
  `requireSession` and releases a session on `DELETE /api/sessions/{id}`. Some servers that answer `newSession`
  still started a fresh session for an unknown id unless `requireSession: true` was set, so a client that relies
  on the refusal sends `requireSession: true` explicitly once it has seen `newSession`: every such server
  refuses then.

### `POST /api/sessions`

Starts a session. The optional body names where it starts, each name an identifier reference as it would be
written in SQL — a bare name folds to upper case, a double-quoted one keeps its case:

```json
{"database": "\"my db\"", "schema": "PUBLIC"}
```

It answers `{"success": true, "sessionId": "…", "newSession": true, …}`. When the database or schema selects
nothing, it answers `{"success": false, "errorMessage": "…", "sessionId": null}` and leaves no session behind.

### `DELETE /api/sessions/{id}`

Releases a session, rolling back a transaction it left open: 200 `{"success": true, …}`, or 404
`{"success": false, …}` when the id names no live session.

### `GET /api/sessions`

`{"activeSessions": 3}`.

## `GET /api/health`

200 with `"status": "healthy"` while the server is up.

## Staged files

The stage functions hand out URLs that point at this server (`http.host`/`http.port`), and the server streams the
staged file at each of them without a session: it is unauthenticated by design. The account's own presigned URL is a
cloud-storage URL that needs no session either; for a file or scoped URL the account asks for a session and
redirects to its cloud storage. `GET` and `HEAD` are served; any other method answers `405`. A failure
answers as an object store does, `Content-Type: application/xml`,
`<Error><Code>…</Code><Message>…</Message></Error>`: `404 NoSuchKey` for a file, stage or path that is not there,
`403 AccessDenied` for an expired URL, `403 SignatureDoesNotMatch` for a token this process did not sign.

| Path | Made by | Valid |
| --- | --- | --- |
| `/presigned/<token>/<file name>` | `GET_PRESIGNED_URL` | until its expiry (3600 seconds by default) |
| `/api/files/<database>/<schema>/<stage>/<path>` | `BUILD_STAGE_FILE_URL`, and a directory table's `FILE_URL` | for as long as the stage exists: the names are read back from their percent-encoded SQL spelling and resolved when the URL is fetched |
| `/api/files/<query id>/<number>/<token>` | `BUILD_SCOPED_FILE_URL` | 24 hours |

A token is signed with a key the process draws at start-up, so a presigned or scoped URL does not outlive the
process that issued it. See the stage functions in `docs/functions.md`.

## Transport

The server turns TCP no-delay on for its sockets (`sun.net.httpserver.nodelay=true`, unless the launcher set the
property itself). Without it a kept-alive connection waits on the client's delayed ACK for every response body —
about 40 ms a statement on Linux and macOS. The JDK reads the property once, when its HTTP server first loads, so
an application that creates another JDK `HttpServer` before embedding `DatabaseHttpServer` sets it at launch.
