# Frostlake in DBeaver

Frostlake ships one JDBC driver and five optional function/runtime modules. Which modules are
available to a query depends entirely on **which JVM's classpath they are on** — and that is decided
by the JDBC URL you connect with, because two of the three URL forms run the engine inside DBeaver
itself.

| URL form | Where the engine runs | Where the module jars go |
| --- | --- | --- |
| `jdbc:frostlake:file:<dir>` | inside DBeaver | DBeaver's **driver libraries** |
| `jdbc:frostlake:direct:<name>` | inside DBeaver | DBeaver's **driver libraries** |
| `jdbc:frostlake://host:port/db` | in the HTTP server | the **server's** classpath |

Get that one table right and everything else follows.

---

## Option A — embedded (recommended for a single user)

The engine runs inside DBeaver, so DBeaver needs every jar. Nothing to start, nothing to keep running.

### 1. Get the jars

If you just want to use Frostlake, download a released build — no checkout, no compile, only
Maven and a network connection:

```bash
./data/download-driver-bundle.sh ~/frostlake-driver geo rt-js
```

Each module's version is resolved from Maven Central independently, so one that releases on its own
schedule still lands on its own latest release. Pin them all with `FROSTLAKE_VERSION=0.0.5`. A module
that has not been published yet is named in the output rather than failing the run — build that one
from source instead.

If you are working on Frostlake itself, or want a module that is not released, build from the tree:

```bash
cd frostlake
./data/build-driver-bundle.sh ~/frostlake-driver geo ai rt-js rt-py
```

Name only the modules you want — each one costs disk and startup time:

| Bundle | Jars | Size |
| --- | --- | --- |
| engine only | 13 | 8.3 MB |
| `geo ai` | 15 | 8.4 MB |
| `geo ai rt-js` | 25 | 70 MB |
| `geo ai rt-js rt-py` | 36 | 196 MB |

`rt-py` is by far the largest — GraalPy's `python-language` jar alone is ~98 MB. Leave it out unless
you actually write Python UDFs.

### 2. Register the driver

**Database → Driver Manager → New**

- **Driver Name**: `Frostlake`
- **Class Name**: `dev.frostlake.jdbc.DatabaseDriver`
- **URL Template**: `jdbc:frostlake:file:{file}`
- **Default Port**: leave blank
- **Libraries** tab → **Add Folder** → `~/frostlake-driver`

Add the *folder*, not the individual jars — that way a rebuilt bundle is picked up without re-adding
anything, and the folder itself lands on the classpath so a `frostlake.properties` dropped beside the
jars is found.

Untick **"Use Java-based property provider"** if DBeaver offers it; the driver reads its own
configuration.

### 3. Connect

**Database → New Database Connection → Frostlake**, then a URL:

```
jdbc:frostlake:file:/home/you/frostlake-data
```

That directory holds the persisted catalog and data (WAL-durable; add `?wal=false` for
snapshot-only). Use `jdbc:frostlake:direct:scratch` instead for a throwaway in-memory database that
lives as long as DBeaver does.

Leave user and password empty.

### 4. Check the modules loaded

```sql
CREATE DATABASE IF NOT EXISTS demo;
USE DATABASE demo;
USE SCHEMA PUBLIC;

-- frostlake-geo
SELECT ST_DISTANCE(ST_MAKEPOINT(0,0), ST_MAKEPOINT(1,1));        -- 157249.628…

-- frostlake-rt-js
CREATE OR REPLACE FUNCTION js_double(x INTEGER) RETURNS INTEGER
  LANGUAGE JAVASCRIPT AS 'return X * 2;';
SELECT js_double(21);                                             -- 42

-- frostlake-rt-py
CREATE OR REPLACE FUNCTION py_shout(s VARCHAR) RETURNS VARCHAR
  LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = 'go' AS $$
def go(s):
    return s.upper()
$$;
SELECT py_shout('hello');                                         -- HELLO

-- frostlake-ai (this one needs no model server)
SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m', 'hello there, world');  -- 4
```

A missing module is a **call-time** error naming the fix, not a startup failure — `CREATE FUNCTION …
LANGUAGE JAVASCRIPT` succeeds without `rt-js`, and only invoking it says:

```
LANGUAGE JAVASCRIPT is not available: add dev.frostlake:frostlake-rt-js to the classpath
```

---

## Option B — HTTP server (recommended for a shared instance)

The engine runs in its own JVM, so **DBeaver needs only the driver**: the engine-only bundle, 13 jars
and 8.3 MB, with no GraalVM in sight. Everyone connecting shares one catalog.

### 1. Start the server with the modules on its classpath

```bash
cd frostlake
./data/build-driver-bundle.sh ~/frostlake-server geo ai rt-js rt-py

java -Dpolyglot.engine.WarnInterpreterOnly=false \
     -cp "$HOME/frostlake-server/*" \
     dev.frostlake.http.DatabaseHttpServer 18082
```

(`data/start-http-server.sh` runs the server through `mvn exec:java` off the engine module alone, so
it will **not** see the optional modules. Use the `java -cp` form above when you want them.)

### 2. Register the driver in DBeaver

Same as Option A, but build the client bundle with no modules at all — the driver talks HTTP and never
loads them:

```bash
./data/build-driver-bundle.sh ~/frostlake-client
```

- **URL Template**: `jdbc:frostlake://{host}:{port}/{database}`
- **Default Port**: `18082`

### 3. Connect

```
jdbc:frostlake://localhost:18082/demo
```

Every module on the server is now usable from DBeaver even though its jars are nowhere near it.

---

## Configuring the modules

Two knobs matter, and both are read the same way: **system property first, then
`frostlake.properties`** — looked for in the working directory, then `~/.frostlake/`, then the
classpath.

For **Option A** the working directory is DBeaver's own install directory, which is no place for your
configuration. Use either of:

**`~/.frostlake/frostlake.properties`** — the tidy choice, and picked up by both the engine and the AI
pack:

```properties
# Cortex AI -> your local Ollama
ai.ollama.url=http://localhost:11434
ai.ollama.model=llama3.2
ai.ollama.embedModel=nomic-embed-text
ai.ollama.timeoutMs=60000

# GraalPy venv whose packages (numpy, pandas, …) Python UDFs may import
python.venv=/opt/graalpy-packages/venv
```

**`dbeaver.ini`** — a `-D` per setting, which beats any file:

```
-Dai.ollama.model=llama3.2
-Dpolyglot.engine.WarnInterpreterOnly=false
```

Add them after the `-vmargs` line. `WarnInterpreterOnly` only silences a GraalVM notice on a stock
JDK; leave it out if you would rather see it.

For **Option B**, configuration belongs to the server: run it from a directory holding
`frostlake.properties`, or pass `-D` flags on its command line. DBeaver needs none of it.

A Python UDF that imports numpy or pandas needs a GraalPy venv — see
`graalpy-packages/README-frostlake.md`. Point at it with `python.venv` above, or per-connection:

```
jdbc:frostlake:file:/home/you/frostlake-data?venv=%2Fopt%2Fgraalpy-packages%2Fvenv
```

(the path is URL-encoded).

---

## Troubleshooting

**`ClassNotFoundException: dev.frostlake.jdbc.DatabaseDriver`** — the driver jar is not in the
library list. Re-add the bundle folder.

**`Unknown function: ST_DISTANCE` / `SNOWFLAKE.CORTEX.SENTIMENT`** — that module is not on the
classpath of the JVM running the engine. For a `file:`/`direct:` URL, rebuild the bundle with the
module named. For a `//host:port` URL, restart the *server* with it.

**`LANGUAGE PYTHON is not available: add dev.frostlake:frostlake-rt-py to the classpath`** — the same
thing, said by the language-runtime SPI.

**`Cannot reach the model server at http://localhost:11434`** — the Cortex functions resolved and
tried to run. Start Ollama and `ollama pull` the model named by `ai.ollama.model`; everything except
the model-backed functions works without it.

**`ModuleNotFoundError: No module named 'numpy'`** — GraalPy is running without a venv. Set
`python.venv`.

**Slow first connection with `rt-py`** — GraalPy initialises on first use. It is a one-off per DBeaver
session.

**`The server no longer holds session …` (SQLState 08003)** — over the HTTP server, the connection sat idle
past the server's 30-minute session limit, or the server restarted, while its session held something a new one
would not: an open transaction, or a USE, SET or ALTER SESSION run in the editor. The statement did not run. Run
it again and it goes to a new session on the connection's own database and schema; repeat the USE or SET first if
it depends on one. A session that held nothing is replaced without a word, and a schema picked in DBeaver's
navigator is put back on the new session too.

**"Data directory … is open in another Frostlake process"** — a `file:` directory takes one process at
a time. Close the other client, or give DBeaver its own directory; the message names the holding
process id. Opening many DBeaver tabs on one directory is fine — they share a single engine and a
single lock.

The lock is an OS lock on `frostlake.lock`, which the kernel releases when its holder ends *however* it
ends, so force-quitting DBeaver no longer leaves a directory stuck. A leftover `frostlake.lock` from a
crashed run is harmless and never needs deleting by hand.
