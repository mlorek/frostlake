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

import dev.frostlake.executor.StatementCount;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.jdbc.DirectConnection;
import dev.frostlake.jdbc.DirectResultSet;
import dev.frostlake.jdbc.DirectStatement;
import dev.frostlake.jdbc.ZonedTimestamp;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantJsonFormat;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * <p>Values are coerced back to the engine's runtime shapes where the JDBC surface flattens them: BINARY
 * {@code byte[]} becomes {@link BinaryValue} again, a semi-structured column's JSON text the engine cell it
 * spells, and a {@code java.sql} temporal the engine's {@code java.time} cell it agrees with. A DML statement's
 * count grid keeps the affected-row count the engine marked it with, so {@code executeUpdate} reports what
 * moved. Statement errors are rethrown as the engine's own RuntimeException — the driver carries it as the
 * SQLException's cause — preserving the embedded throw-on-error contract and its exception classes.
 */
public class FrostlakeJdbcEngine extends DatabaseEngine {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The days either side of 1970 whose milliseconds a long still counts: the reach of a java.sql.Date. */
    private static final long SQL_DATE_REACH_DAYS = Long.MAX_VALUE / 86_400_000L;

    private final DirectConnection connection;

    /**
     * The driver executes by calling back into this very engine — without the flag that call
     * would re-enter the override and recurse. Set while a statement is inside the JDBC layer,
     * so the nested call runs the embedded engine directly.
     */
    private boolean insideDriver;

    public FrostlakeJdbcEngine() {
        // The harness reads this engine's settings directly, so its connection shares them.
        this.connection = new DirectConnection(this, false);
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
            // The suite freely passes multi-statement scripts through engine.execute, and the driver runs the real
            // driver's MULTI_STATEMENT_COUNT gate: each submission declares its own statement count, as the live
            // harness's does, so a script passes the gate and the session's count stays the session's — an
            // EXECUTE IMMEDIATE among the statements is held to the count the request declares.
            // A text of no statement declares none, and is answered as the empty statement it is.
            ((DirectStatement) statement).setParameter("MULTI_STATEMENT_COUNT", StatementCount.countStatements(sql));
            statement.execute(sql);
            queryId = ((DirectStatement) statement).getQueryId();
            // Walked by what each position HOLDS, not by what getMoreResults() answered: like Snowflake's
            // driver, it answers true for an update count that more results follow.
            while (true) {
                final java.sql.ResultSet rows = statement.getResultSet();
                if (rows != null) {
                    try (final java.sql.ResultSet rs = rows) {
                        results.add(convert(rs));
                    }
                } else {
                    final int count = statement.getUpdateCount();
                    if (count == -1) {
                        break;
                    }
                    // The statement still built the grid the embedded engine answers with — a DML count grid
                    // or a status line — read back through the same getters.
                    try (final java.sql.ResultSet grid = ((DirectStatement) statement).getResultGrid()) {
                        final ResultSet engineGrid = convert(grid);
                        requireReportedCount(sql, count, engineGrid);
                        results.add(engineGrid);
                    }
                }
                statement.getMoreResults();
            }
            syncConnectionToEngine();
        } catch (final SQLException e) {
            syncFinallyAfterError();
            throw unwrap(e);
        }
        return new ExecutionResult(true, results, null, queryId);
    }

    /**
     * The driver's reported update count must be the one the engine marked the statement with: the audit this
     * harness exists for, applied to the count as well as to the values.
     */
    private static void requireReportedCount(final String sql, final int reported, final ResultSet grid) {
        final Long marked = grid.getJdbcUpdateCount();
        if (marked == null || marked.longValue() != reported) {
            throw new IllegalStateException("The driver reported update count " + reported + " for a result the engine"
                + " marked " + marked + ": " + sql);
        }
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
        final boolean[] interval = new boolean[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            final String typeName = String.valueOf(metaData.getColumnTypeName(i)).toUpperCase(Locale.ROOT);
            semiStructured[i - 1] = typeName.startsWith("VARIANT") || typeName.startsWith("OBJECT")
                || typeName.startsWith("ARRAY");
            interval[i - 1] = typeName.startsWith("INTERVAL");
        }
        final ResultSet engineResult = rs instanceof DirectResultSet ? ((DirectResultSet) rs).getEngineResultSet() : null;
        final boolean scriptingAnswer = engineResult != null && engineResult.isScriptingAnswer();
        final List<Row> rows = new ArrayList<Row>();
        while (rs.next()) {
            final List<Object> values = new ArrayList<Object>(columnCount);
            final Row engineRow = engineResult == null ? null : engineResult.getRows().get(rows.size());
            for (int i = 1; i <= columnCount; i++) {
                if (interval[i - 1]) {
                    // The driver hands an interval back as the account's driver does — a BigDecimal of
                    // nanoseconds, a Duration or a Period, by the column's width — not as the engine's own
                    // value: its text is read (so the getter path is still exercised) and the engine's cell
                    // handed back, which is the shape the engine-level assertions compare.
                    final String text = rs.getString(i);
                    values.add(engineRow == null ? text : engineRow.getValue(i - 1));
                    continue;
                }
                values.add(coerce(rs.getObject(i), semiStructured[i - 1], scriptingAnswer,
                    engineRow == null ? null : engineRow.getValue(i - 1)));
            }
            rows.add(new Row(values));
        }
        final ResultSet converted = new ResultSet(columns, rows);
        if (engineResult != null && engineResult.getUpdateCount() != null) {
            // The driver answers a DML statement with its count grid; the mark that makes the grid an
            // affected-row count rather than a query naming its columns alike stays with the result.
            converted.markUpdateCount(engineResult.getUpdateCount().longValue());
        }
        if (engineResult != null) {
            converted.markJdbcUpdateCount(engineResult.getJdbcUpdateCount());
        }
        return converted;
    }

    /**
     * The inverse of the driver's value flattening, back to the engine's runtime shapes. A
     * semi-structured COLUMN can still hold a plain string cell, which the driver spells as its JSON
     * text, quotes included, exactly as it spells a {@link VariantValue} string: when the engine's
     * own cell is that plain string and the driver's text is its spelling, the string is what comes
     * back. Text that reads back as a variant spelled exactly as the driver owes it comes back as the
     * engine's own cell when that cell has the same client text, so a number or a boolean the engine
     * holds unwrapped keeps its class, and as the variant read otherwise. Any other text that parses
     * as JSON is re-wrapped, and a bare word stays a string.
     *
     * @param value the value the driver's getter returned
     * @param semiStructured whether the column is VARIANT, OBJECT or ARRAY
     * @param scriptingAnswer whether the result is a Snowflake Scripting answer, whose arrays and objects the driver
     *     lays out across lines
     * @param engineValue the engine's cell behind it, or null when the result did not come from it
     * @return the engine-shaped value
     */
    private Object coerce(final Object value, final boolean semiStructured, final boolean scriptingAnswer,
            final Object engineValue) {
        if (value instanceof byte[]) {
            return BinaryValue.of((byte[]) value);
        }
        if (value instanceof Timestamp || value instanceof Date || value instanceof Time) {
            return engineTemporal(value, engineValue);
        }
        if (semiStructured && value instanceof String) {
            final String text = (String) value;
            if (engineValue instanceof String && VariantJsonText.unwrappedStringText((String) engineValue).equals(text)) {
                return engineValue;
            }
            // The driver hands a VARIANT back as its client text, a DOUBLE in the fifteen-decimal form.
            // Read the way PARSE_JSON reads it, the exponent restores the DOUBLE family and with it the
            // canonical text. The reading is kept only when it spells the text back exactly, in the layout
            // the driver owes the result, which is what tells a variant's text from a plain string cell
            // that merely looks like JSON.
            final JsonNode read = JsonTypeHelper.parseLenient(text);
            if (read != null) {
                final VariantValue reread = VariantValue.ofNode(read);
                if (clientSpelling(reread, scriptingAnswer).equals(text)) {
                    return engineValue != null && sameClientText(reread, engineValue) ? engineValue : reread;
                }
            }
            try {
                JSON.readTree(text);
                return VariantValue.of(text);
            } catch (final RuntimeException notJson) {
                return value;
            }
        }
        return value;
    }

    /**
     * The text the driver owes a variant: an array or object of a scripting answer laid out across lines, any other
     * laid out at the session's JSON_INDENT (compact at width zero), and a scalar its client text.
     *
     * @param variant the variant read back
     * @param scriptingAnswer whether the result is a Snowflake Scripting answer
     * @return its client text
     */
    private static String clientSpelling(final VariantValue variant, final boolean scriptingAnswer) {
        final String laidOut = scriptingAnswer ? VariantJsonFormat.scriptingAnswerText(variant) : null;
        return laidOut != null ? laidOut
            : VariantJsonFormat.render(variant.node(), VariantJsonText.clientTextOf(variant));
    }

    /**
     * Whether the engine's cell is the variant read back: the same client text, so a DOUBLE and a number of the
     * same value still differ.
     *
     * @param reread the variant read back from the driver's text
     * @param engineValue the engine's cell, a variant or a value the engine holds unwrapped
     * @return whether the two spell alike
     */
    private static boolean sameClientText(final VariantValue reread, final Object engineValue) {
        final VariantValue engineVariant = engineValue instanceof VariantValue ? (VariantValue) engineValue
            : VariantValue.ofNode(ArrayFunctionHelper.toNode(JSON, engineValue));
        return VariantJsonText.clientTextOf(engineVariant).equals(VariantJsonText.clientTextOf(reread));
    }

    /**
     * The inverse of the driver's temporal mapping. The driver answers a DATE, TIME or timestamp as the
     * {@code java.sql} class its metadata names — as the account's driver does — carrying the value's
     * LOCAL part, while the engine-level assertions compare the {@code java.time} value the engine holds
     * (a zoned timestamp keeps its offset there, and a TIME its fractional seconds). When the driver's
     * value is exactly the engine cell's local part, the engine cell is what comes back; otherwise the
     * driver's own value, as {@code java.time}, so a real disagreement still reaches the assertion. A
     * TIMESTAMP_LTZ or TIMESTAMP_TZ cell is the exception: the driver hands it back as its moment and the
     * offset it prints its wall clock at, which together are the engine's zoned cell, compared whole. A
     * DATE farther from 1970 than a {@code java.sql.Date} reaches comes back as the engine's cell, there
     * being nothing the driver's value can carry to compare.
     *
     * @param value the driver's {@code java.sql.Date}, {@code Time} or {@code Timestamp}
     * @param engineValue the engine's cell behind it, or null when the result did not come from it
     * @return the engine-shaped value
     */
    private static Object engineTemporal(final Object value, final Object engineValue) {
        if (value instanceof ZonedTimestamp) {
            final ZonedTimestamp zoned = (ZonedTimestamp) value;
            final OffsetDateTime driverMoment = OffsetDateTime.ofInstant(zoned.toInstant(), zoned.getZone());
            final OffsetDateTime engineMoment = engineValue instanceof OffsetDateTime ? (OffsetDateTime) engineValue
                : engineValue instanceof ZonedDateTime ? ((ZonedDateTime) engineValue).toOffsetDateTime() : null;
            return driverMoment.equals(engineMoment) ? engineValue : driverMoment;
        }
        final Object driverLocal;
        final Object engineLocal;
        if (value instanceof Timestamp) {
            driverLocal = ((Timestamp) value).toLocalDateTime();
            engineLocal = engineValue instanceof OffsetDateTime ? ((OffsetDateTime) engineValue).toLocalDateTime()
                : engineValue instanceof ZonedDateTime ? ((ZonedDateTime) engineValue).toLocalDateTime()
                : engineValue instanceof LocalDate ? ((LocalDate) engineValue).atStartOfDay()
                : engineValue;
        } else if (value instanceof Date) {
            if (engineValue instanceof LocalDate
                    && Math.abs(((LocalDate) engineValue).toEpochDay()) > SQL_DATE_REACH_DAYS) {
                // A java.sql.Date counts the milliseconds from 1970 in a long, which a date this far away overflows.
                return engineValue;
            }
            driverLocal = ((Date) value).toLocalDate();
            engineLocal = engineValue instanceof LocalDateTime ? ((LocalDateTime) engineValue).toLocalDate()
                : engineValue instanceof ZonedDateTime ? ((ZonedDateTime) engineValue).toLocalDate()
                : engineValue;
        } else {
            // java.sql.Time carries whole seconds, so the engine's fractional seconds are compared cut.
            driverLocal = ((Time) value).toLocalTime();
            engineLocal = engineValue instanceof LocalTime ? ((LocalTime) engineValue).withNano(0) : engineValue;
        }
        return driverLocal.equals(engineLocal) ? engineValue : driverLocal;
    }

    /** Rethrow the engine's own exception, which the driver carries as the SQLException cause. */
    private RuntimeException unwrap(final SQLException e) {
        if (e.getCause() instanceof RuntimeException) {
            return (RuntimeException) e.getCause();
        }
        return new RuntimeException(e.getMessage(), e);
    }
}
