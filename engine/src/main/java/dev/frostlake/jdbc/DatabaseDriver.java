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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String DIRECT_PREFIX = "jdbc:frostlake:direct:";
    private static final Map<String, DatabaseEngine> DIRECT_ENGINES = new HashMap<>();
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
        return url != null && (url.startsWith(URL_PREFIX) || url.startsWith(DIRECT_PREFIX));
    }

    /**
     * Open an in-process connection (no HTTP server): {@code jdbc:frostlake:direct:<name>[?database=DB&schema=S]}.
     * All connections with the same {@code <name>} share one embedded {@link DatabaseEngine} for the JVM's
     * lifetime (an empty name uses a single default engine); different names are isolated. Optional
     * database/schema (URL query or Properties) set the connection's current context.
     */
    private Connection connectDirect(final String url, final Properties info) throws SQLException {
        try {
            final int q = url.indexOf('?');
            final String name = (q >= 0 ? url.substring(DIRECT_PREFIX.length(), q)
                                        : url.substring(DIRECT_PREFIX.length())).trim();

            DatabaseEngine engine;
            synchronized (DIRECT_ENGINES) {
                engine = DIRECT_ENGINES.get(name);
                if (engine == null) {
                    engine = new DatabaseEngine();
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

    private String parseParameter(final String url, final String paramName) {
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
