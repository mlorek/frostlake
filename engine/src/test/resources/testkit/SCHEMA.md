# Test description format (v1)

Language-neutral, data-only SQL test definitions. A runner in any language (this repo's Java
reference, or a driver repo in JS / Go / PHP / Rust) executes the same files against its transport
and reports the same statuses. Commands, expected values, and expected exceptions are all part of
the description — no test logic lives in code.

## File layout

One suite per `*.json` file:

```json
{
  "suite": "dml",
  "description": "human text",
  "tags": ["dml"],
  "tests": [
    {
      "name": "update-where",
      "tags": ["optional", "extra", "tags"],
      "skip": {"backends": ["http"], "reason": "why"},
      "steps": [
        {"sql": "CREATE TABLE t (id INTEGER, v VARCHAR)"},
        {"sql": "INSERT INTO t VALUES (1,'a'), (2,'b')", "expect": {"updateCount": 2}},
        {"sql": "UPDATE t SET v = 'x' WHERE id = 2", "expect": {"updateCount": 1}},
        {"sql": "SELECT v FROM t WHERE id = 2", "expect": {"value": "x"}},
        {"sql": "SELECT * FROM missing", "expect": {"error": {"messageContains": "does not exist"}}}
      ]
    }
  ]
}
```

## Execution contract

1. **Per-test isolation.** Before each test the runner resets context by executing, in order:
   `ALTER SESSION SET MULTI_STATEMENT_COUNT = 0` · `CREATE OR REPLACE DATABASE test_db` ·
   `USE DATABASE test_db` · `CREATE OR REPLACE SCHEMA test_schema` · `USE SCHEMA test_schema`.
   Every test starts in an empty `test_db.test_schema`, on a session that accepts a script.
   The first statement matters: a request holds **one** statement unless the session or the request
   asks for more, and several cases send a script of two or three. `0` means "any number", which is
   what a test session wants — an exact count would then refuse the single statements around it.
2. **Steps run in order in one session** (session state — USE, variables, open transactions —
   must carry across steps; over HTTP that means reusing the returned `sessionId`).
3. A step with no `expect` must simply succeed. The first failed check stops the test.

## Expectations (`expect`)

| key | meaning |
|---|---|
| `value` | first column of the first row equals this scalar (after normalization); `null` = SQL NULL |
| `rows` | full grid equality; cells are strings or `null`. Unordered by default; `"ordered": true` for ORDER BY results |
| `rowCount` | number of rows returned |
| `columns` | result column names, case-insensitive, in order *(capability `COLUMN_NAMES`)* |
| `updateCount` | DML-affected row count *(capability `UPDATE_COUNT`)* |
| `error` | the statement must fail. Optional fields: `messageContains` (case-insensitive substring), `code`, `sqlState` *(the latter two need capability `ERROR_CODE`)* |

**Value normalization** (both sides, before comparing): `null`/empty → `NULL`; booleans
case-insensitive; anything numeric compares as a number rounded to 10 significant digits
(`2` == `2.000000`, `3.500000` == `3.5`); everything else is an exact trimmed string.

**Update-count derivation.** Frostlake and Snowflake report DML counts as a result grid
(`number of rows inserted` …). When the transport has no out-of-band count, a runner derives it
from that grid: single row, all columns named `number of …` → count = first cell.

## Capability model

A backend declares what its transport can express:

| capability | engine | jdbc | http | snowflake |
|---|---|---|---|---|
| `SESSION` | ✓ | ✓ | ✓ (sessionId) | ✓ |
| `COLUMN_NAMES` | ✓ | ✓ | ✓ | ✓ |
| `UPDATE_COUNT` | ✓ | ✓ (derived) | ✓ (derived) | ✓ (derived) |
| `ERROR_CODE` | — | — | — | ✓ |

A check that needs a missing capability is **not a failure** — the runner records it and emits
`results/missing-apis-<backend>.md`: the list of APIs to add to that transport later. (Today all
three Frostlake transports report errors as message-only; the expectations for codes are already in
the test files, so the day the API exists the checks light up without touching a single test.)

## Statuses

`PASS` · `FAIL` (an expectation mismatched — with step number, detail, and the SQL) ·
`ERROR` (transport/infrastructure problem) · `SKIP` (test's `skip` clause names this backend).

## Writing a runner in another language (drivers: js, go, php, rust)

A conforming runner is small (~150 lines): parse the JSON, connect through the driver under test,
implement the reset sequence, run steps, apply the normalization rules above, honor capabilities,
and emit the TSV (`suite · test · status · failedStep · detail · ms`). The Java reference
implementation is `src/main/java/dev/frostlake/testkit/` — `Runner`, `Compare` (normalization), and
one `Backend` per transport (~100 lines each) to copy the shape from.
