/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.jdbc;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * JDBC Driver for Frostlake SQL Engine
 *
 * Usage:
 *   Class.forName("dev.frostlake.jdbc.DatabaseDriver");
 *   Connection conn = DriverManager.getConnection("jdbc:frostlake://localhost:8080", props);
 */
public class DatabaseDriver implements Driver {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseDriver.class);

    private static final String URL_PREFIX = "jdbc:frostlake://";
    // In-process (no HTTP server) scheme: jdbc:frostlake:direct:<name>. Connections sharing a <name> share one
    // embedded DatabaseEngine for the JVM's lifetime; different names are isolated (mirrors H2's mem-DB model).
    // Both in-process schemes also take the account identity the engine reports —
    // ?account= (locator), ?accountName=, ?organization= and ?region= — see identityOverrides.
    private static final String DIRECT_PREFIX = "jdbc:frostlake:direct:";
    private static final Map<String, DatabaseEngine> DIRECT_ENGINES = new HashMap<>();
    // Embedded-persistent scheme (H2's file-DB model):
    //   jdbc:frostlake:file:<dir>[?database=DB&schema=S&wal=false&venv=<graalpy-venv-dir>
    //                               &account=LOC&accountName=ACC&organization=ORG&region=AWS_EU_WEST_1]
    // The engine runs in this JVM and persists to <dir>; state restores on the first connection and survives
    // restarts. Connections naming the same directory (by canonical path) share one engine; a lock file guards
    // the directory against a second process. Durable via the WAL by default, ?wal=false switches to
    // snapshot-only persistence (autosave + save-on-close; a hard kill can lose the last interval). ?venv=
    // (URL-encoded path) pins python.venv for the rt-py runtime, replacing an ambient frostlake.properties.
    private static final String FILE_PREFIX = "jdbc:frostlake:file:";
    private static final Map<String, DatabaseEngine> FILE_ENGINES = new HashMap<>();
    private static final Map<String, Thread> FILE_SHUTDOWN_HOOKS = new HashMap<>();
    /** The OS lock held on each open data directory, released when its engine closes. */
    private static final Map<String, DataDirectoryLock> FILE_LOCKS = new HashMap<>();
    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 0;

    // Register driver with DriverManager
    static {
        try {
            DriverManager.registerDriver(new DatabaseDriver());
            logger.info("Frostlake JDBC Driver registered");
        } catch (final SQLException e) {
            logger.error("Failed to register Frostlake JDBC Driver", e);
            throw new RuntimeException("Failed to register driver", e);
        }
    }

    @Override
    public Connection connect(final String url, final Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        if (url.startsWith(DIRECT_PREFIX)) {
            return connectDirect(url, info);
        }
        if (url.startsWith(FILE_PREFIX)) {
            return connectFile(url, info);
        }

        try {
            // Parse URL: jdbc:frostlake://host:port/database?schema=PUBLIC
            String baseUrl = parseBaseUrl(url);
            String database = parseDatabase(url);
            // Fall back to ?database=... query parameter or Properties if not in path
            if (database == null) {
                String dbParam = parseParameter(url, "database");
                if (dbParam != null && !dbParam.isEmpty()) database = dbParam.toUpperCase();
            }
            if (database == null && info != null) {
                String dbProp = info.getProperty("database");
                if (dbProp != null && !dbProp.isEmpty()) database = dbProp.toUpperCase();
            }
            String schema = info != null ? info.getProperty("schema", parseParameter(url, "schema"))
                                         : parseParameter(url, "schema");

            logger.debug("Connecting to {} (database: {}, schema: {})", baseUrl, database, schema);

            return new DatabaseConnection(baseUrl, database, schema, info);

        } catch (final Exception e) {
            throw new SQLException("Failed to create connection: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean acceptsURL(final String url) {
        return url != null
            && (url.startsWith(URL_PREFIX) || url.startsWith(DIRECT_PREFIX) || url.startsWith(FILE_PREFIX));
    }

    /**
     * Open an in-process connection (no HTTP server): {@code jdbc:frostlake:direct:<name>[?database=DB&schema=S]}.
     * All connections with the same {@code <name>} share one embedded {@link DatabaseEngine} for the JVM's
     * lifetime (an empty name uses a single default engine); different names are isolated. Optional
     * database/schema (URL query or Properties) set the connection's current context.
     */
    /**
     * Register a pre-built engine under a direct-URL name, so {@code jdbc:frostlake:direct:<name>}
     * connections reach it instead of lazily creating a fresh empty engine. This is how a harness
     * hands out engine-instance clones ({@link DatabaseEngine#cloneInstance()}): clone the migrated
     * template, register the clone under a per-test-class name, and point that class's JDBC url at
     * it — each class then runs on its own engine (and its own monitor, so classes execute SQL in
     * parallel instead of serializing on one shared engine).
     */
    public static void registerDirectEngine(final String name, final DatabaseEngine engine) {
        synchronized (DIRECT_ENGINES) {
            DIRECT_ENGINES.put(name.trim(), engine);
        }
    }

    /** Remove a registered direct engine; returns it (for shutdown) or null if the name is unknown. */
    public static DatabaseEngine unregisterDirectEngine(final String name) {
        synchronized (DIRECT_ENGINES) {
            return DIRECT_ENGINES.remove(name.trim());
        }
    }

    /**
     * The account identity a URL names: {@code ?account=}, {@code ?accountName=}, {@code ?region=} and
     * {@code ?organization=}, each overriding the corresponding {@code frostlake.properties} value,
     * which in turn overrides the built-in default.
     *
     * <p>Only the IN-PROCESS URL forms take these. Over {@code jdbc:frostlake://host:port/db} the engine
     * belongs to a server that is already running and shared by every client, so a client cannot
     * redefine the account it is connecting TO — configure that server instead.
     *
     * <p>They also only apply on the connection that CREATES the engine: engines are shared per
     * {@code direct:} name and per {@code file:} directory, so a second connection naming a different
     * account joins the first one's engine rather than reconfiguring it. The same is true of
     * {@code ?venv=}, and for the same reason.
     */
    private static Properties identityOverrides(final String url) {
        final Properties overrides = new Properties();
        putIfNamed(overrides, EngineConfig.PROP_ACCOUNT_ID, parseParameter(url, "account"));
        putIfNamed(overrides, EngineConfig.PROP_ACCOUNT_NAME, parseParameter(url, "accountName"));
        putIfNamed(overrides, EngineConfig.PROP_SNOWFLAKE_REGION, parseParameter(url, "region"));
        putIfNamed(overrides, EngineConfig.PROP_ORGANIZATION_NAME, parseParameter(url, "organization"));
        return overrides;
    }

    private static void putIfNamed(final Properties into, final String key, final String value) {
        if (value != null && !value.isEmpty()) {
            into.setProperty(key, URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
    }

    private Connection connectDirect(final String url, final Properties info) throws SQLException {
        try {
            final int q = url.indexOf('?');
            final String name = (q >= 0 ? url.substring(DIRECT_PREFIX.length(), q)
                                        : url.substring(DIRECT_PREFIX.length())).trim();

            DatabaseEngine engine;
            synchronized (DIRECT_ENGINES) {
                engine = DIRECT_ENGINES.get(name);
                if (engine == null) {
                    engine = new DatabaseEngine(new EngineConfig(identityOverrides(url)));
                    DIRECT_ENGINES.put(name, engine);
                }
            }

            final DirectConnection connection = new DirectConnection(engine);

            String database = parseParameter(url, "database");
            if ((database == null || database.isEmpty()) && info != null) {
                database = info.getProperty("database");
            }
            final String schema = info != null ? info.getProperty("schema", parseParameter(url, "schema"))
                                                : parseParameter(url, "schema");
            if (database != null && !database.isEmpty()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("USE DATABASE " + database);
                }
            }
            if (schema != null && !schema.isEmpty()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("USE SCHEMA " + schema);
                }
            }
            logger.debug("Opened in-process connection (engine '{}', database: {}, schema: {})", name, database, schema);
            return connection;
        } catch (final Exception e) {
            throw new SQLException("Failed to create in-process connection: " + e.getMessage(), e);
        }
    }

    /**
     * Open an embedded-persistent connection: {@code jdbc:frostlake:file:<dir>[?database=DB&schema=S&wal=false]}.
     * The engine runs in this JVM with its state persisted under {@code <dir>}: the first connection restores
     * whatever a previous run left there, and connections naming the same directory (canonical path) share one
     * engine. Durability defaults to the write-ahead log (every committed statement fsync'd and replayed on the
     * next open — nothing committed is lost, even on a hard kill); {@code ?wal=false} switches to snapshot-only
     * persistence (autosave every interval + a final save on clean shutdown). A {@code frostlake.lock} file
     * guards the directory: a second process gets a clear error instead of silently corrupting the data.
     * {@code ?venv=<dir>} (URL-encoded) sets {@code python.venv} for the optional rt-py runtime, so Python
     * UDF handlers can import the venv's packages without any ambient {@code frostlake.properties}.
     */
    private Connection connectFile(final String url, final Properties info) throws SQLException {
        try {
            final int q = url.indexOf('?');
            final String rawPath = (q >= 0 ? url.substring(FILE_PREFIX.length(), q)
                                           : url.substring(FILE_PREFIX.length())).trim();
            if (rawPath.isEmpty()) {
                throw new SQLException(
                    "jdbc:frostlake:file: URL must name a data directory, e.g. jdbc:frostlake:file:/var/frostlake/dev");
            }
            final String key = new File(rawPath).getCanonicalPath();

            final String rawVenv = parseParameter(url, "venv");
            final String venv = rawVenv == null ? null : URLDecoder.decode(rawVenv, StandardCharsets.UTF_8);

            DatabaseEngine engine;
            synchronized (FILE_ENGINES) {
                engine = FILE_ENGINES.get(key);
                if (engine == null) {
                    engine = openFileEngine(key, "false".equalsIgnoreCase(parseParameter(url, "wal")),
                        venv, identityOverrides(url));
                    FILE_ENGINES.put(key, engine);
                }
            }

            final DirectConnection connection = new DirectConnection(engine);

            String database = parseParameter(url, "database");
            if ((database == null || database.isEmpty()) && info != null) {
                database = info.getProperty("database");
            }
            final String schema = info != null ? info.getProperty("schema", parseParameter(url, "schema"))
                                                : parseParameter(url, "schema");
            if (database != null && !database.isEmpty()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("USE DATABASE " + database);
                }
            }
            if (schema != null && !schema.isEmpty()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("USE SCHEMA " + schema);
                }
            }
            logger.debug("Opened file-backed connection (dir '{}', database: {}, schema: {})", key, database, schema);
            return connection;
        } catch (final SQLException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Failed to open file-backed connection: " + e.getMessage(), e);
        }
    }

    /**
     * Create the engine behind a data directory: take the directory lock, pin persistence to the directory
     * (WAL by default, snapshot-only when {@code snapshotOnly}), and register a JVM shutdown hook so an
     * abandoned engine still persists its final state and releases the lock at exit. Callers hold the
     * {@code FILE_ENGINES} monitor.
     */
    private static DatabaseEngine openFileEngine(final String canonicalDir, final boolean snapshotOnly,
                                                 final String venv, final Properties identity)
            throws IOException {
        final Path dir = Path.of(canonicalDir);
        Files.createDirectories(dir);
        final DataDirectoryLock directoryLock = DataDirectoryLock.acquire(dir);

        final Properties overrides = new Properties();
        overrides.putAll(identity);
        overrides.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, canonicalDir);
        overrides.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, canonicalDir + File.separator + "wal.log");
        if (venv != null && !venv.isEmpty()) {
            overrides.setProperty(EngineConfig.PROP_PYTHON_VENV, venv);
        }
        if (snapshotOnly) {
            overrides.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
            overrides.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "false");
        } else {
            // WAL mode: the log is the authoritative restore path (snapshot persistence stays off — the
            // engine ignores it under WAL anyway). Periodic checkpoints bound the log and recovery time.
            overrides.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "false");
            overrides.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
            overrides.setProperty(EngineConfig.PROP_DURABILITY_CHECKPOINT_INTERVAL, "500");
        }

        final DatabaseEngine engine;
        try {
            engine = new DatabaseEngine(new EngineConfig(overrides));
        } catch (final RuntimeException e) {
            directoryLock.release();
            throw e;
        }

        final DatabaseEngine opened = engine;
        final Thread hook = new Thread(new Runnable() {
            @Override
            public void run() {
                opened.shutdown();
                directoryLock.release();
            }
        }, "frostlake-file-engine-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        FILE_SHUTDOWN_HOOKS.put(canonicalDir, hook);
        FILE_LOCKS.put(canonicalDir, directoryLock);
        return engine;
    }

    /**
     * Close the shared engine behind a {@code jdbc:frostlake:file:<dir>} URL: persist its final state, release
     * the directory lock and forget the engine, so a later connection re-opens the directory (restoring the
     * state) fresh. Returns the closed engine, or null if the directory has no open engine. This is the
     * in-JVM equivalent of the exit-time shutdown hook, for embedders that want to release a data directory
     * without exiting.
     */
    public static DatabaseEngine closeFileEngine(final String dataDir) throws IOException {
        final String key = new File(dataDir).getCanonicalPath();
        final DatabaseEngine engine;
        final Thread hook;
        final DataDirectoryLock directoryLock;
        synchronized (FILE_ENGINES) {
            engine = FILE_ENGINES.remove(key);
            hook = FILE_SHUTDOWN_HOOKS.remove(key);
            directoryLock = FILE_LOCKS.remove(key);
        }
        if (engine == null) {
            return null;
        }
        if (hook != null) {
            Runtime.getRuntime().removeShutdownHook(hook);
        }
        engine.shutdown();
        if (directoryLock != null) {
            directoryLock.release();
        }
        return engine;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) {
        DriverPropertyInfo[] props = new DriverPropertyInfo[3];

        props[0] = new DriverPropertyInfo("database", info.getProperty("database"));
        props[0].description = "Database name";
        props[0].required = false;

        props[1] = new DriverPropertyInfo("schema", info.getProperty("schema", "PUBLIC"));
        props[1].description = "Schema name";
        props[1].required = false;

        props[2] = new DriverPropertyInfo("sessionId", info.getProperty("sessionId"));
        props[2].description = "Existing session ID to reuse";
        props[2].required = false;

        return props;
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public boolean jdbcCompliant() {
        return false; // We don't implement all JDBC features
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger not supported");
    }

    // Helper methods

    private String parseBaseUrl(final String url) throws SQLException {
        // jdbc:frostlake://host:port/database -> http://host:port
        String withoutPrefix = url.substring(URL_PREFIX.length());
        int slashIndex = withoutPrefix.indexOf('/');
        int questionIndex = withoutPrefix.indexOf('?');

        String hostPort;
        if (slashIndex > 0) {
            hostPort = withoutPrefix.substring(0, slashIndex);
        } else if (questionIndex > 0) {
            hostPort = withoutPrefix.substring(0, questionIndex);
        } else {
            hostPort = withoutPrefix;
        }

        return "http://" + hostPort;
    }

    private String parseDatabase(final String url) {
        // jdbc:frostlake://host:port/database
        String withoutPrefix = url.substring(URL_PREFIX.length());
        int slashIndex = withoutPrefix.indexOf('/');
        if (slashIndex < 0) {
            return null;
        }

        String database;
        int questionIndex = withoutPrefix.indexOf('?', slashIndex);
        if (questionIndex > 0) {
            database = withoutPrefix.substring(slashIndex + 1, questionIndex);
        } else {
            database = withoutPrefix.substring(slashIndex + 1);
        }

        // Database names are case-insensitive and stored in uppercase
        return database != null && !database.isEmpty() ? database.toUpperCase() : null;
    }

    private static String parseParameter(final String url, final String paramName) {
        int questionIndex = url.indexOf('?');
        if (questionIndex < 0) {
            return null;
        }

        String queryString = url.substring(questionIndex + 1);
        String[] params = queryString.split("&");

        for (final String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }

        return null;
    }
}
