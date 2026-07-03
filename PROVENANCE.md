# Provenance & Independence

Frostlake (`dev.frostlake:frostlake-db`) is an **independent, clean-room implementation**
of a Snowflake-compatible SQL engine.

## How it was built

- Frostlake was developed solely from **publicly available sources**: the public Snowflake
  documentation (<https://docs.snowflake.com>) and the SQL standard.
- **No Snowflake source code** was accessed, decompiled, or copied, and no confidential or
  trade-secret material of Snowflake Inc. was used.
- All engine code — the ANTLR SQL grammar, parser, executor, operator pipeline, storage,
  built-in functions, JDBC driver, HTTP layer, and console clients — is original work.

## API-compatibility surface

- Frostlake reimplements the *behavior* and *interface* of Snowflake SQL (dialect, function
  names, semantics) for compatibility. Reimplementing a functional interface is not copying of
  protected expression — cf. *Google LLC v. Oracle America, Inc.*, 593 U.S. 1 (2021), and, in
  the EU, *SAS Institute Inc. v. World Programming Ltd.* (CJEU C-406/10).
- The `com.snowflake.snowpark_java` package contains **independently written stub classes**
  that match the public Snowpark API signatures for source-level compatibility. They do **not**
  contain copied Snowpark implementation code.

## Trademark

Frostlake is **not affiliated with, endorsed by, or sponsored by Snowflake Inc.** "Snowflake"
and "Snowpark" are trademarks of Snowflake Inc., used in this project only *nominatively*, to
describe compatibility.

## Copyright & license

Copyright 2026 MLorek. Licensed under the Apache License, Version 2.0 (see [`LICENSE`](LICENSE)).
