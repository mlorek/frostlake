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

package dev.frostlake;

import dev.frostlake.jdbc.DirectConnection;
import dev.frostlake.jdbc.DirectResultSet;
import dev.frostlake.jdbc.DirectStatement;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link DatabaseEngine} whose SQL surface ({@code execute} / {@code executeQuery} /
 * {@code executeUpdate}) runs through Frostlake's OWN JDBC driver — the {@code FL_JDBC=1} backend
 * for {@link BaseDatabaseTest}, turning the whole engine suite into a driver-surface audit.
 * Every statement travels {@link DirectConnection} → {@code DirectStatement} →
 * {@link DirectResultSet}, and values are read back through the JDBC getters, so marshaling
 * regressions fail tests that would otherwise never leave the engine API.
 *
 * <p>The connection wraps THIS engine, so internal accessors (catalog, managers, function
 * registry) see exactly the state the SQL produced — unlike {@code SF_LIVE}, tests that poke
 * engine internals stay meaningful in this mode.
 *
 * <p>Values are coerced back to the engine's runtime shapes where the JDBC surface flattens
 * them: BINARY {@code byte[]} becomes {@link BinaryValue} again and a semi-structured column's
 * JSON text becomes {@link VariantValue}; temporals need no coercion because the driver already
 * surfaces the engine's {@code java.time} values. Statement errors are rethrown as the engine's
 * own RuntimeException — the driver carries it as the SQLException's cause — preserving the
 * embedded throw-on-error contract and its exception classes.
 */
public class FrostlakeJdbcEngine extends DatabaseEngine {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DirectConnection connection;

    /**
     * The driver executes by calling back into this very engine — without the flag that call
     * would re-enter the override and recurse. Set while a statement is inside the JDBC layer,
     * so the nested call runs the embedded engine directly.
     */
    private boolean insideDriver;

    public FrostlakeJdbcEngine() {
        this.connection = new DirectConnection(this);
        // The suite freely passes multi-statement scripts through engine.execute, and the driver
        // now runs the real driver's MULTI_STATEMENT_COUNT gate — lift it for the harness the way
        // a real caller would, which also exercises the driver's ALTER SESSION detector.
        try (final Statement bootstrap = connection.createStatement()) {
            bootstrap.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ExecutionResult execute(final String sql) {
        if (insideDriver) {
            return super.execute(sql);
        }
        insideDriver = true;
        try {
            return executeOverJdbc(sql);
        } finally {
            insideDriver = false;
        }
    }

    private ExecutionResult executeOverJdbc(final String sql) {
        final List<ResultSet> results = new ArrayList<ResultSet>();
        String queryId = null;
        try (final Statement statement = connection.createStatement()) {
            syncEngineToConnection();
            boolean isResultSet = statement.execute(sql);
            queryId = ((DirectStatement) statement).getQueryId();
            while (true) {
                if (isResultSet) {
                    try (final java.sql.ResultSet rs = statement.getResultSet()) {
                        results.add(convert(rs));
                    }
                } else if (statement.getUpdateCount() == -1) {
                    break;
                }
                isResultSet = statement.getMoreResults();
            }
            syncConnectionToEngine();
        } catch (final SQLException e) {
            syncFinallyAfterError();
            throw unwrap(e);
        }
        return new ExecutionResult(true, results, null, queryId);
    }

    // The connection keeps SQL-driven session state (USE, CREATE-activation, ALTER SESSION SET
    // AUTOCOMMIT) in its own per-session fields, while tests read and move state through engine
    // internals between statements. The two views are converged around every statement: after a
    // statement the connection is authoritative (its state is pushed into the engine's globals),
    // and before a statement the engine wins only if internals moved it since the last push.
    private String lastSyncedDatabase;
    private String lastSyncedSchema;
    private boolean lastSyncedAutoCommit = true;

    private void syncEngineToConnection() throws SQLException {
        final String db = getCurrentDatabase();
        if (db != null && !db.equals(lastSyncedDatabase) && !db.equals(connection.getCatalog())) {
            connection.setCatalog(db);
        }
        final String schema = getCurrentSchema();
        if (schema != null && !schema.equals(lastSyncedSchema) && !schema.equals(connection.getSchema())) {
            connection.setSchema(schema);
        }
        final boolean autoCommit = getTransactionManager().isAutoCommit();
        if (autoCommit != lastSyncedAutoCommit && autoCommit != connection.getAutoCommit()) {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void syncConnectionToEngine() throws SQLException {
        lastSyncedDatabase = connection.getCatalog();
        lastSyncedSchema = connection.getSchema();
        getCatalog().restoreContext(lastSyncedDatabase, lastSyncedSchema);
        lastSyncedAutoCommit = connection.getAutoCommit();
        getTransactionManager().setAutoCommit(lastSyncedAutoCommit);
    }

    /** A failed statement still moved no context, but the engine globals must not drift. */
    private void syncFinallyAfterError() {
        try {
            syncConnectionToEngine();
        } catch (final SQLException ignored) {
            // best-effort convergence after a failed statement
        }
    }

    @Override
    public void shutdown() {
        try {
            connection.close();
        } catch (final SQLException e) {
            // Closing the JDBC view must not mask the engine shutdown.
        }
        super.shutdown();
    }

    /** Reads the whole JDBC result through the getters, keeping the engine-native typed columns. */
    private ResultSet convert(final java.sql.ResultSet rs) throws SQLException {
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();
        final List<ResultSetColumn> columns;
        if (rs instanceof DirectResultSet) {
            columns = ((DirectResultSet) rs).getEngineResultSet().getColumns();
        } else {
            columns = new ArrayList<ResultSetColumn>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(new ResultSetColumn(metaData.getColumnLabel(i), StringType.VARCHAR));
            }
        }
        final boolean[] semiStructured = new boolean[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            final String typeName = String.valueOf(metaData.getColumnTypeName(i)).toUpperCase();
            semiStructured[i - 1] = typeName.startsWith("VARIANT") || typeName.startsWith("OBJECT")
                || typeName.startsWith("ARRAY");
        }
        final List<Row> rows = new ArrayList<Row>();
        while (rs.next()) {
            final List<Object> values = new ArrayList<Object>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                values.add(coerce(rs.getObject(i), semiStructured[i - 1]));
            }
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }

    /**
     * The inverse of the driver's value flattening, back to the engine's runtime shapes. A
     * semi-structured COLUMN can still hold a plain string cell (the driver flattens only
     * {@link VariantValue}s to JSON text), so only text that parses as JSON is re-wrapped —
     * a bare word stays the plain string the engine held.
     */
    private Object coerce(final Object value, final boolean semiStructured) {
        if (value instanceof byte[]) {
            return BinaryValue.of((byte[]) value);
        }
        if (semiStructured && value instanceof String) {
            try {
                JSON.readTree((String) value);
                return VariantValue.of((String) value);
            } catch (final RuntimeException notJson) {
                return value;
            }
        }
        return value;
    }

    /** Rethrow the engine's own exception, which the driver carries as the SQLException cause. */
    private RuntimeException unwrap(final SQLException e) {
        if (e.getCause() instanceof RuntimeException) {
            return (RuntimeException) e.getCause();
        }
        return new RuntimeException(e.getMessage(), e);
    }
}
