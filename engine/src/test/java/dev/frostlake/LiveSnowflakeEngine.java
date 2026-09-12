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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import net.snowflake.client.api.statement.SnowflakeStatement;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link DatabaseEngine} whose SQL surface ({@code execute} / {@code executeQuery} /
 * {@code executeUpdate}) runs against a LIVE Snowflake account over JDBC — the {@code SF_LIVE=1}
 * backend for the test base classes, letting the same suite validate Frostlake and Snowflake give
 * the same answers. Only the SQL entry points are rerouted; internal accessors (catalog, managers,
 * function registry) still belong to the embedded engine this class inherits, so tests that poke
 * engine internals exercise Frostlake even in live mode.
 *
 * <p>Engine-style multi-statement scripts are split with the engine's OWN parser and submitted one
 * statement at a time. Snowflake's server-side multi-statement splitter mis-splits unquoted
 * procedure bodies at their internal semicolons; the engine's grammar understands
 * {@code BEGIN…END}, {@code $$} bodies and string literals, so its statement boundaries are the
 * ones the embedded engine itself would execute. Text the engine cannot parse is submitted as-is.
 *
 * <p>JDBC values are coerced toward the engine's runtime shapes so assertions written against
 * Frostlake keep working: temporals become {@code java.time} locals, BINARY becomes
 * {@link BinaryValue}, whole {@code BigDecimal}s become {@code Long}, and semi-structured columns'
 * pretty-printed JSON is re-serialized compactly (Snowflake pretty-prints VARIANT output over
 * JDBC; the engine's canonical text is compact). DML submitted over JDBC surfaces as bare update
 * counts, so Snowflake's actual one-cell result ("number of rows inserted" / updated / deleted) is
 * reconstructed from the statement's leading keyword. Statement errors surface as
 * {@link RuntimeException}s, matching the embedded engine's throw-on-error contract.
 */
public class LiveSnowflakeEngine extends DatabaseEngine {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public ExecutionResult execute(final String sql) {
        markDirtyIfSessionMutating(sql);
        final List<ResultSet> results = new ArrayList<ResultSet>();
        String queryId = null;
        for (final String statementText : splitStatements(sql)) {
            markIfAccountObjectStatement(statementText);
            queryId = executeOne(statementText, results);
        }
        return new ExecutionResult(true, results, null, queryId);
    }

    private String executeOne(final String sql, final List<ResultSet> results) {
        try (final Statement statement = LiveSnowflake.shared().createStatement()) {
            LiveSnowflake.pinSingleStatement(statement);
            // The driver classifies COPY (and the stage file operations) as non-queries under
            // execute(), answering only an update count and hiding the result rows the server
            // sends (and the embedded engine returns). executeQuery surfaces them.
            if (needsResultSetRoute(sql)) {
                try (final java.sql.ResultSet rs = statement.executeQuery(sql)) {
                    results.add(convert(rs));
                }
                return queryIdOf(statement);
            }
            boolean isResultSet = statement.execute(sql);
            while (true) {
                if (isResultSet) {
                    try (final java.sql.ResultSet rs = statement.getResultSet()) {
                        results.add(convert(rs));
                    }
                } else {
                    final int count = statement.getUpdateCount();
                    if (count == -1) {
                        break;
                    }
                    results.add(updateCountResult(count, sql));
                }
                isResultSet = statement.getMoreResults();
            }
            return queryIdOf(statement);
        } catch (final SQLException e) {
            LiveSnowflake.markSharedDirty();   // a failed script can abandon a transaction mid-flight
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static boolean needsResultSetRoute(final String sql) {
        final String trimmed = sql.trim();
        int end = 0;
        while (end < trimmed.length() && Character.isLetter(trimmed.charAt(end))) {
            end++;
        }
        final String first = trimmed.substring(0, end).toUpperCase();
        return "COPY".equals(first) || "PUT".equals(first) || "GET".equals(first)
            || "LIST".equals(first) || "REMOVE".equals(first) || "RM".equals(first);
    }

    private String queryIdOf(final Statement statement) {
        try {
            if (statement.isWrapperFor(SnowflakeStatement.class)) {
                return statement.unwrap(SnowflakeStatement.class).getQueryID();
            }
        } catch (final SQLException ignored) {
            // Query-id decoration only — never fail the statement over it.
        }
        return null;
    }

    /**
     * Splits a script into its top-level statements using the engine's grammar (each
     * {@code flowChain} — a statement, or a {@code ->>} chain that must stay together — is one
     * submission). Unparseable text is returned whole so Snowflake reports its own error.
     */
    private List<String> splitStatements(final String sql) {
        try {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final FrostlakeParser parser = new FrostlakeParser(tokens);
            final boolean[] failed = new boolean[1];
            final BaseErrorListener silent = new BaseErrorListener() {
                @Override
                public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
                        final int line, final int charPositionInLine, final String msg,
                        final RecognitionException e) {
                    failed[0] = true;
                }
            };
            lexer.removeErrorListeners();
            lexer.addErrorListener(silent);
            parser.removeErrorListeners();
            parser.addErrorListener(silent);
            final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
            if (failed[0] || script.flowChain().isEmpty()) {
                return Collections.singletonList(sql);
            }
            // A single statement goes over VERBATIM. Slicing it from its first token's start index
            // would drop the leading whitespace, and Snowflake reports a syntax error at the token's
            // own line and column — so the split would silently move the error being measured, and a
            // position test would read the account's answer to text it never sent.
            if (script.flowChain().size() == 1) {
                return Collections.singletonList(sql);
            }
            // Only text the SOURCE separates with a semicolon is split. Where the engine's parser
            // finds two statements with nothing between them, that reading is the engine's own and
            // may be exactly the divergence under test — forwarding the halves separately would ask
            // the account about text it was never given, and a wrong reading would come back green.
            final List<String> statements = new ArrayList<String>();
            for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
                if (!statements.isEmpty()) {
                    final int gapStart = script.flowChain().get(statements.size() - 1)
                        .getStop().getStopIndex() + 1;
                    if (sql.substring(gapStart, chain.getStart().getStartIndex()).indexOf(';') < 0) {
                        return Collections.singletonList(sql);
                    }
                }
                statements.add(sql.substring(chain.getStart().getStartIndex(),
                    chain.getStop().getStopIndex() + 1));
            }
            return statements;
        } catch (final RuntimeException e) {
            return Collections.singletonList(sql);
        }
    }

    /**
     * Marks the shared session dirty for statements that can leave transaction, autocommit, role or
     * warehouse state behind — detected from the statement's leading lexer tokens, so comments and
     * case don't fool it. The next test's setup then restores the session baseline.
     */
    private void markDirtyIfSessionMutating(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        Token first = lexer.nextToken();
        while (first.getType() != Token.EOF && first.getChannel() != Token.DEFAULT_CHANNEL) {
            first = lexer.nextToken();
        }
        if (first.getType() == Token.EOF) {
            return;
        }
        final String keyword = first.getText().toUpperCase();
        if (keyword.equals("BEGIN") || keyword.equals("START") || keyword.equals("ALTER")
                || keyword.equals("USE") || keyword.equals("CREATE") || keyword.equals("DROP")
                || keyword.equals("CALL") || keyword.equals("EXECUTE") || keyword.equals("DECLARE")) {
            Token second = lexer.nextToken();
            while (second.getType() != Token.EOF && second.getChannel() != Token.DEFAULT_CHANNEL) {
                second = lexer.nextToken();
            }
            final String detail = second.getType() == Token.EOF ? "" : second.getText().toUpperCase();
            final boolean mutating;
            switch (keyword) {
                case "BEGIN":
                case "START":
                case "DECLARE":
                    // BEGIN/BEGIN TRANSACTION/anonymous blocks: blocks can run transaction control
                    // and procedure calls, so treat all of them as potentially session-mutating.
                    mutating = true;
                    break;
                case "CALL":
                case "EXECUTE":
                    // Procedures and dynamic SQL can do anything — reset afterwards.
                    mutating = true;
                    break;
                case "ALTER":
                    mutating = detail.equals("SESSION");
                    break;
                case "USE":
                    mutating = detail.equals("ROLE") || detail.equals("WAREHOUSE")
                        || detail.equals("SECONDARY");
                    break;
                default:
                    // CREATE/DROP WAREHOUSE change the active warehouse (CREATE auto-uses it).
                    mutating = detail.equals("WAREHOUSE");
                    break;
            }
            if (mutating) {
                LiveSnowflake.markSharedDirty();
            }
        }
    }

    /**
     * Flags statements that create, drop or alter an ACCOUNT-level object (database, role, user,
     * warehouse) so {@link BaseDatabaseTest}'s teardown knows the account-object diff in
     * {@link LiveAccountObjects} is worth running — the vast majority of tests touch nothing outside
     * {@code test_db} and must not pay for the {@code SHOW}s.
     *
     * <p>Read off the leading lexer tokens, skipping the modifiers that can sit between the verb and
     * the object kind ({@code CREATE OR REPLACE DATABASE}, {@code CREATE TRANSIENT DATABASE}), so
     * comments, case and whitespace cannot fool it and a table column merely NAMED {@code user}
     * cannot trip it.
     */
    private void markIfAccountObjectStatement(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        final Token verb = nextDefaultToken(lexer);
        if (verb.getType() == Token.EOF || !isObjectStatementVerb(verb.getText().toUpperCase())) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            final Token token = nextDefaultToken(lexer);
            if (token.getType() == Token.EOF) {
                return;
            }
            final String text = token.getText().toUpperCase();
            if (isAccountObjectKind(text)) {
                LiveAccountObjects.markAccountObjectStatement();
                return;
            }
            if (!isObjectKindModifier(text)) {
                return;
            }
        }
    }

    private Token nextDefaultToken(final FrostlakeLexer lexer) {
        Token token = lexer.nextToken();
        while (token.getType() != Token.EOF && token.getChannel() != Token.DEFAULT_CHANNEL) {
            token = lexer.nextToken();
        }
        return token;
    }

    private boolean isObjectStatementVerb(final String keyword) {
        switch (keyword) {
            case "CREATE":
            case "DROP":
            case "ALTER":
            case "UNDROP":
                return true;
            default:
                return false;
        }
    }

    private boolean isObjectKindModifier(final String keyword) {
        switch (keyword) {
            case "OR":
            case "REPLACE":
            case "TRANSIENT":
            case "TEMPORARY":
            case "TEMP":
            case "VOLATILE":
            case "SECURE":
            case "LOCAL":
            case "GLOBAL":
                return true;
            default:
                return false;
        }
    }

    private boolean isAccountObjectKind(final String keyword) {
        switch (keyword) {
            case "DATABASE":
            case "ROLE":
            case "USER":
            case "WAREHOUSE":
                return true;
            default:
                return false;
        }
    }

    private ResultSet convert(final java.sql.ResultSet rs) throws SQLException {
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        final boolean[] semiStructured = new boolean[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            columns.add(new ResultSetColumn(metaData.getColumnLabel(i), declaredType(metaData, i)));
            final String typeName = String.valueOf(metaData.getColumnTypeName(i)).toUpperCase();
            semiStructured[i - 1] = typeName.equals("VARIANT") || typeName.equals("OBJECT")
                || typeName.equals("ARRAY");
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

    private Object coerce(final Object value, final boolean semiStructured) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.sql.Time) {
            return ((java.sql.Time) value).toLocalTime();
        }
        if (value instanceof byte[]) {
            return BinaryValue.of((byte[]) value);
        }
        if (value instanceof BigDecimal) {
            // Coerce to Long only when the value carries NO scale — a NUMBER(8,6) result like
            // 5.000000 must stay a scaled BigDecimal, exactly as the engine now produces for
            // division/AVG (stripping trailing zeros here erased Snowflake's result scale).
            final BigDecimal decimal = (BigDecimal) value;
            if (decimal.scale() <= 0 && decimal.precision() <= 18) {
                return decimal.longValueExact();
            }
            return decimal;
        }
        if (semiStructured && value instanceof String) {
            // A semi-structured column arrives as JSON text. Snowflake keeps a path result VARIANT —
            // so a string leaf comes back QUOTED ("txt") and a number as its JSON text — while the
            // engine unwraps path results to plain runtime values. Unwrap JSON SCALARS to the same
            // Java shapes the engine produces so value comparisons line up; objects and arrays come
            // back compact, each number token spelled exactly as the account sent it.
            try {
                final JsonNode node = JSON.readTree((String) value);
                if (node.isTextual()) {
                    return node.asText();
                }
                if (node.isBoolean()) {
                    return Boolean.valueOf(node.booleanValue());
                }
                if (node.isNumber() && !node.isIntegralNumber()) {
                    // A DOUBLE leaf stays a double, the shape the engine unwraps a path result to. It
                    // used to hop through BigDecimal.valueOf, which has no negative zero: the account
                    // hands GET(PARSE_JSON('[-0e0]'), 0) back as -0.000000000000000e+00, and the hop
                    // read it as 0.0 — a sign lost in this harness, never on the account.
                    return Double.valueOf(node.doubleValue());
                }
                if (node.isNumber()) {
                    return coerce(node.decimalValue(), false);
                }
                return compactKeepingNumbers((String) value);
            } catch (final RuntimeException notJson) {
                return value;
            }
        }
        return value;
    }

    /**
     * The document compacted, with every number token spelled exactly as the account sent it. A tree's
     * re-serialisation respells a DOUBLE in Java's shortest form — 1.000000000000000e+00 as 1.0 — which
     * is the one place the account's display and the engine's canonical text differ.
     */
    private static String compactKeepingNumbers(final String text) {
        final StringBuilder out = new StringBuilder();
        boolean separate = false;
        try (JsonParser parser = JSON.createParser(text)) {
            JsonToken token = parser.nextToken();
            while (token != null) {
                if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    out.append(token == JsonToken.END_OBJECT ? '}' : ']');
                    separate = true;
                } else {
                    if (separate) {
                        out.append(',');
                    }
                    if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                        out.append(token == JsonToken.START_OBJECT ? '{' : '[');
                        separate = false;
                    } else if (token == JsonToken.PROPERTY_NAME) {
                        out.append(StringNode.valueOf(parser.currentName()).toString()).append(':');
                        separate = false;
                    } else {
                        out.append(token == JsonToken.VALUE_STRING
                            ? StringNode.valueOf(parser.getString()).toString() : parser.getString());
                        separate = true;
                    }
                }
                token = parser.nextToken();
            }
        }
        return out.toString();
    }

    /**
     * The account's own type for a result column, read from the driver's metadata. The JDBC type INT
     * is not enough on its own — VARIANT, OBJECT and ARRAY all arrive as {@code Types.VARCHAR} — so the
     * TYPE NAME decides the family and precision/scale carry the parameters. Measured on the account,
     * over the JSON result format this session forces:
     *
     * <pre>
     *   INT              NUMBER precision 38 scale 0        TIME           TIME  scale 9
     *   NUMBER(10,2)     NUMBER precision 10 scale 2        TIMESTAMP_NTZ  TIMESTAMPNTZ scale 9
     *   FLOAT            DOUBLE                             TIMESTAMP_LTZ  TIMESTAMPLTZ scale 9
     *   VARCHAR(5)       VARCHAR precision 5                TIMESTAMP_TZ   TIMESTAMPTZ  scale 9
     *   TEXT             VARCHAR precision 16777216         VARIANT/OBJECT/ARRAY by NAME
     *   BINARY(8)        BINARY precision 8                 DATE           DATE
     * </pre>
     *
     * <p>Without this every column read VARCHAR, so no test could compare a declared type against the
     * account at all.
     */
    private DataType declaredType(final ResultSetMetaData metaData, final int index) throws SQLException {
        final String typeName = String.valueOf(metaData.getColumnTypeName(index)).toUpperCase();
        final int precision = metaData.getPrecision(index);
        final int scale = metaData.getScale(index);
        switch (typeName) {
            case "NUMBER":
            case "NUMERIC":
            case "DECIMAL":
                return new NumericType("NUMBER", precision, scale);
            case "DOUBLE":
            case "DOUBLE PRECISION":
            case "FLOAT":
            case "REAL":
                // The driver carries NO precision or scale for this family (both read 0), so only the
                // family itself is measurable here — compare the name, not the parameters.
                return new NumericType("FLOAT", precision, scale);
            case "VARCHAR":
            case "TEXT":
            case "STRING":
            case "CHAR":
                return new StringType("VARCHAR", precision);
            case "BINARY":
            case "VARBINARY":
                return new BinaryType("BINARY", precision);
            case "BOOLEAN":
                return new BooleanType();
            case "DATE":
                return new DateTimeType("DATE", 0, false);
            case "TIME":
                return new DateTimeType("TIME", scale, false);
            case "TIMESTAMPNTZ":
            case "TIMESTAMP_NTZ":
            case "TIMESTAMP":
            case "DATETIME":
                return new DateTimeType("TIMESTAMP_NTZ", scale, false);
            case "TIMESTAMPLTZ":
            case "TIMESTAMP_LTZ":
                return new DateTimeType("TIMESTAMP_LTZ", scale, true);
            case "TIMESTAMPTZ":
            case "TIMESTAMP_TZ":
                return new DateTimeType("TIMESTAMP_TZ", scale, true);
            case "VARIANT":
                return new VariantType();
            case "OBJECT":
                return new ObjectType();
            case "ARRAY":
                return new ArrayType(new VariantType());
            default:
                return StringType.VARCHAR;
        }
    }

    private ResultSet updateCountResult(final int count, final String sql) {
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        columns.add(new ResultSetColumn(countColumnName(sql), StringType.VARCHAR));
        final List<Row> rows = new ArrayList<Row>();
        final List<Object> values = new ArrayList<Object>();
        values.add(Long.valueOf(count));
        rows.add(new Row(values));
        return new ResultSet(columns, rows);
    }

    /**
     * Snowflake's actual DML result is a one-cell result set named for the verb; over JDBC only the
     * bare count survives, so rebuild the name from the statement's first lexer token. (MERGE
     * genuinely returns per-action columns that a single count cannot reconstruct — documented
     * harness caveat.)
     */
    private String countColumnName(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        Token first = lexer.nextToken();
        while (first.getType() != Token.EOF && first.getChannel() != Token.DEFAULT_CHANNEL) {
            first = lexer.nextToken();
        }
        final String keyword = first.getType() == Token.EOF ? "" : first.getText().toUpperCase();
        switch (keyword) {
            case "INSERT":
                return "number of rows inserted";
            case "UPDATE":
                return "number of rows updated";
            case "DELETE":
                return "number of rows deleted";
            default:
                return "number of rows affected";
        }
    }
}
