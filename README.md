# Frostlake

An embeddable SQL engine that emulates core Snowflake features — for fast, credit-free
local and CI testing. Ships an in-process `DatabaseEngine`, a JDBC driver, an HTTP server,
and console clients.

- **Maven:** `dev.frostlake:frostlake-db`
- **License:** [Apache-2.0](LICENSE)
- **Homepage:** <https://frostlake.dev>

## What it is

Frostlake runs Snowflake-compatible SQL — DDL/DML, CTEs, window functions, PIVOT/UNPIVOT,
Snowflake Scripting procedures, JS/Python/Scala UDFs, streams, tasks, and role-based
security — against a lightweight in-memory engine, so you can develop and test Snowflake
SQL locally without a Snowflake account or credits.

## Build

```bash
mvn clean package -DskipTests   # build the jar
mvn test                        # full test suite
mvn verify                      # + Apache RAT license-header audit
```

## Trademarks & Disclaimer

Frostlake is an independent project and is **not affiliated with, endorsed by, or sponsored
by Snowflake Inc.** "Snowflake" and "Snowpark" are trademarks of Snowflake Inc., used here
only nominatively, to describe compatibility.

See [`PROVENANCE.md`](PROVENANCE.md) for clean-room / independence details and [`NOTICE`](NOTICE)
for third-party attributions.
