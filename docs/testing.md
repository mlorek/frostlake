# Testing Frostlake

The engine's tests run in one of three modes, chosen by environment variables; the same test classes serve all
three. Run Maven from the `frostlake/` project root.

## Embedded (the default)

```bash
mvn test                                   # every module's suite, perf tests excluded
mvn -pl engine test -Dtest=ClassName       # one engine test class
```

Each test that extends `BaseDatabaseTest` gets a fresh in-process `DatabaseEngine` with `test_db.test_schema`
current. Tests tagged `perf` (all in `perf-tests`) run only when the `perf` or `perf-mem` profile is active, e.g.
`mvn verify -Pperf`.

## Through Frostlake's JDBC driver (`FL_JDBC=1`)

```bash
FL_JDBC=1 mvn -pl engine test
```

`BaseDatabaseTest` sends its SQL through Frostlake's own JDBC driver, a direct connection around the test's
engine, so the whole suite exercises the driver: statement execution, result-set getters and value marshaling.
Tests that read engine internals still see the same engine.

## Against a live Snowflake account (`SF_LIVE=1`)

```bash
SF_LIVE=1 SF_USER=… SF_PASS=… SF_URL=… mvn -pl engine test -Dtest=ClassName
```

| variable | meaning |
|---|---|
| `SF_LIVE` | `1` sends the SQL of both test bases, `BaseDatabaseTest` and `BaseJdbcTest`, to the account instead of the embedded engine |
| `SF_USER`, `SF_PASS` | the login |
| `SF_URL` | the account host, e.g. `myorg-myaccount.snowflakecomputing.com` |
| `SF_WAREHOUSE` | optional: the warehouse to use; without it, the user's default warehouse |

The same suite then checks that Frostlake and Snowflake answer alike. A live run is stateful: every test
recreates `test_db` on the account. The test bases' teardown drops the account-level objects — databases, roles,
users, warehouses — created since the first base-class test started, except `test_db` and the claim database; a
`BaseDatabaseTest` test runs that cleanup only when one of its own statements creates, alters, drops or undrops such
an object. The corpus runner on `snowflake` drops only `test_db`, so whatever its cases create outside it stays on
the account. Tests that read engine internals (the catalog,
the managers) still exercise the embedded engine.

### One live run per account

Two live runs on one account destroy each other. Each run's `test_db` is the other's, and each run's teardown
drops the objects the other created after its own baseline, so one run's objects vanish under the other. The
failures do not look like a collision: `This session does not have a current schema`, a table missing right after
its `CREATE`, classes that fail in one run and pass in the next.

The harness therefore claims the account before its first live statement, and a second run refuses to start:

- The claim is one row in `FROSTLAKE_LIVE_CLAIM.PUBLIC.ACCOUNT_CLAIM`, in a small transient database the harness
  creates on first use and never drops. The row names the holding run — a random run id, and the user, host,
  process id and working directory — and carries two stamps from the account's clock: when the run claimed the
  account, and its last heartbeat.
- A run claims with one conditional `UPDATE` that succeeds only when the row is free, already this run's, or
  stale. Of two runs that start together, exactly one wins.
- While the run lives, a heartbeat refreshes its stamp every 5 minutes on a session of its own. At JVM exit the
  run drops its `test_db` and then frees the row.
- A run that finds the account claimed fails every live test at once, naming the holder:

  ```
  The live account is claimed by another SF_LIVE run, so this run refuses to start: two runs on one account drop
  each other's objects. Holder: alice@build-7 pid 4242 in /work/frostlake/engine (run 7d0c9ed7-…), claimed 312 s
  ago, last heartbeat 12 s ago. If that run has died, its claim goes stale 1788 s from now; to free it sooner, run
  UPDATE FROSTLAKE_LIVE_CLAIM.PUBLIC.ACCOUNT_CLAIM SET RUN_ID = NULL WHERE RUN_ID = '7d0c9ed7-…'.
  ```

- A claim goes stale 30 minutes after its last heartbeat, so a run that dies without its exit hook (killed
  outright, or on a lost machine) blocks the account for at most that long — or until someone who knows it is dead
  runs the `UPDATE` the refusal prints. A run whose heartbeat finds its claim gone — taken over after the run
  stalled past the window, freed by hand, or its claim table dropped — stops using the account: every live test
  after that fails at once, and the harness's cleanups drop nothing, neither `test_db` nor the account objects
  created since the run started.
- A heartbeat and a release only ever touch a row that still carries the run's own id; a run never frees another
  run's claim, and overwrites one only by taking over a stale claim.

The claim keeps runs of this harness apart. Other live clients — an ad-hoc probe, a driver's corpus runner — do
not take it, and a running suite's teardown can still drop what they create outside `test_db`. Keep them apart
from a live run yourself; on one machine, a file lock that a live run holds exclusively and every other live
client holds shared does it.

## The testkit corpus

`engine/src/test/resources/testkit/suites/*.json` holds language-neutral SQL cases (the format is in
`testkit/SCHEMA.md`), and `dev.frostlake.testkit.JsonSuiteTest` runs them all in `mvn test`. Driver repositories
replay the same files with runners of their own.

```bash
mvn -pl engine test -Dtest=JsonSuiteTest -Dtestkit.backend=http -Dtestkit.suites=dml,ddl-alter
```

`-Dtestkit.backend=engine|jdbc|http|snowflake` picks the transport (`FL_JDBC=1` means `jdbc` and `SF_LIVE=1`
means `snowflake`); `-Dtestkit.suites=a,b` runs only the suites whose name contains a listed word — a live replay
of the whole corpus takes hours.

Every expectation that a live account can run records what the account answers; a case the account cannot run
carries a `skip` naming `snowflake`, with the reason. Where Frostlake answers differently on a transport, the case
carries a `skip` naming that backend, with the reason. On `engine`, `jdbc` and `http` the runner replays a
skipped case anyway:

- when the replay fails, the case is reported as skipped, with the skip's reason;
- when it passes, the case fails with `unexpected pass on <backend>: remove the skip ("<reason>")`: the
  divergence the skip recorded is gone, and the skip must go with it.

The replay runs on a fresh backend of its own — a new engine, a driver connection to a new engine, or a new
server — so the objects and session state the case leaves behind cannot reach the cases after it (files it writes to the
user stage `@~` or a simulated `s3://` location stay under `~/.frostlake_stages`, which every engine shares). A run attached to a running server
with `-Dtestkit.url` replays on a new session of that server instead, which keeps only the session's state apart:
the objects the case creates outside `test_db` stay on the server, as any case's do.

On `snowflake` a skipped case is not run at all: a live replay costs warehouse time and can leave account state.
