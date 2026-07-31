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

package dev.frostlake.executor;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.OperatorFunctionNames;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SHOW / DESCRIBE handlers for routines and metadata objects: functions, procedures, tags, file formats,
 * masking / row-access policies. Extracted from {@link ShowCommandExecutor}, which delegates here.
 */
final class ShowRoutineExecutor {

    private final Catalog catalog;
    private final FunctionRegistry functionRegistry;

    ShowRoutineExecutor(final Catalog catalog, final FunctionRegistry functionRegistry) {
        this.catalog = catalog;
        this.functionRegistry = functionRegistry;
    }

    public ResultSet showProcedures(final String schemaName, final boolean userOnly) {
        final List<ResultSetColumn> columns = procedureColumns();
        final List<Row> rows = new ArrayList<>();

        // User-defined procedures in the current (or named) schema, if one is selected.
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName != null && scName != null) {
            appendUserProcedureRows(catalog.getDatabase(dbName).getSchema(scName), dbName, rows);
        }

        // SHOW PROCEDURES = the built-in catalog plus the user procedures in scope, and SHOW USER
        // PROCEDURES drops the former — the same split SHOW FUNCTIONS makes, live-verified on a real
        // account (with one user procedure in the current schema, SHOW PROCEDURES answers 33,
        // SHOW BUILTIN PROCEDURES 32 and SHOW USER PROCEDURES 1).
        if (!userOnly) {
            appendBuiltinProcedureRows(rows);
        }
        return new ResultSet(columns, rows);
    }

    /**
     * SHOW BUILTIN PROCEDURES: the built-in catalog only, never user-defined procedures.
     *
     * <p>Live-verified on a real account: with one user procedure in the current schema it
     * returns 32 rows to SHOW PROCEDURES' 33, the user procedure is in none of them, and the listing
     * ignores scope — {@code IN SCHEMA} and {@code IN DATABASE} both still answer 32.
     */
    public ResultSet showBuiltinProcedures() {
        final List<Row> rows = new ArrayList<>();
        appendBuiltinProcedureRows(rows);
        return new ResultSet(procedureColumns(), rows);
    }

    /**
     * One row per built-in procedure the engine can CALL — of which there are none, so this appends
     * nothing.
     *
     * <p>This is not an oversight and must not become a hand-maintained list of the names a real account
     * happens to ship: like the function listing, this one describes what <em>this</em> engine dispatches.
     * {@code CALL} resolves a name through {@code Schema.getProcedure} and nothing else
     * ({@code SQLCommandVisitor.visitCallStatement} raises "Procedure not found" the moment the catalog
     * misses), so every procedure Frostlake can run is a user procedure in a schema, and the built-in
     * half of the listing is structurally empty. The SYSTEM$ routines the engine implements are
     * FUNCTIONS, not procedures — a real account agrees (
     * {@code SHOW BUILTIN PROCEDURES} does not list SYSTEM$WAIT while {@code SHOW BUILTIN FUNCTIONS}
     * does), so {@code appendBuiltinFunctionRows} lists them instead.
     *
     * <p>When the engine grows a built-in procedure, add it to whatever source {@code CALL} learns to
     * dispatch it from and enumerate that source here — the one mistake to avoid is a second, separate
     * list that the dispatcher never reads.
     */
    private void appendBuiltinProcedureRows(final List<Row> rows) {
        // Intentionally empty — see the javadoc above.
    }

    /**
     * SHOW PROCEDURES IN DATABASE &lt;db&gt;: the built-in catalog plus the user procedures of every
     * schema in the database, mirroring {@link #showFunctionsInDatabase(String)}.
     *
     * <p>Live-verified: with two user procedures in one schema of a two-schema database,
     * {@code IN DATABASE} answers 34 (32 built-in + 2) and {@code IN SCHEMA} the other schema 32.
     * {@link #appendBuiltinProcedureRows} contributes nothing here because Frostlake dispatches no
     * built-in procedures — the call is what keeps the two listings structurally identical, so a
     * built-in procedure would show up in both the moment one exists.
     */
    public ResultSet showProceduresInDatabase(final String databaseName) {
        final List<ResultSetColumn> columns = procedureColumns();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(databaseName).getAllSchemas()) {
            appendUserProcedureRows(schema, databaseName, rows);
        }
        appendBuiltinProcedureRows(rows);
        return new ResultSet(columns, rows);
    }

    /** SHOW USER PROCEDURES IN DATABASE &lt;db&gt;: the same listing with the built-in half dropped. */
    public ResultSet showUserProceduresInDatabase(final String databaseName) {
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(databaseName).getAllSchemas()) {
            appendUserProcedureRows(schema, databaseName, rows);
        }
        return new ResultSet(procedureColumns(), rows);
    }

    private void appendUserProcedureRows(final Schema schema, final String dbName, final List<Row> rows) {
        for (final Procedure proc : schema.getProcedures()) {
            final String sig = proc.getName() + buildArgSig(proc.getParameters())
                + " RETURN " + proc.getReturnType().getName();
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOnText(proc.getCreatedTime()),
                proc.getName(),
                schema.getName(),
                "N", "N", "N",
                proc.getParameters().size(), proc.getParameters().size(),
                sig,
                routineDescription(proc.getComment(), "user-defined procedure"),
                dbName,
                "N", "N", "N",
                null, null
            )));
        }
    }

    /**
     * The SHOW PROCEDURES column shape: the first 16 of the 20 {@link #functionColumns()}, which is to
     * say every one of them except the four a procedure has no answer for
     * ({@code is_external_function}, {@code language}, {@code is_memoizable}, {@code is_data_metric}).
     *
     * <p>Live-verified on a real account: all six procedure listings — plain, USER, BUILTIN,
     * each with and without TERSE — return exactly these 16 names in this order.
     *
     * <p>Frostlake used to end this list with {@code is_external_function, language, execute_as}. The
     * last two are not SHOW PROCEDURES columns at all on a real account; both surface under
     * {@code DESCRIBE PROCEDURE} instead, which answers {@code language | SQL} and
     * {@code execute as | OWNER} property rows (live-verified the same day), and which
     * {@link #describeProcedure(String)} already reports.
     */
    private List<ResultSetColumn> procedureColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("is_builtin", StringType.VARCHAR),
            new ResultSetColumn("is_aggregate", StringType.VARCHAR),
            new ResultSetColumn("is_ansi", StringType.VARCHAR),
            new ResultSetColumn("min_num_arguments", NumericType.INTEGER),
            new ResultSetColumn("max_num_arguments", NumericType.INTEGER),
            new ResultSetColumn("arguments", StringType.VARCHAR),
            new ResultSetColumn("description", StringType.VARCHAR),
            new ResultSetColumn("catalog_name", StringType.VARCHAR),
            new ResultSetColumn("is_table_function", StringType.VARCHAR),
            new ResultSetColumn("valid_for_clustering", StringType.VARCHAR),
            new ResultSetColumn("is_secure", StringType.VARCHAR),
            new ResultSetColumn("secrets", StringType.VARCHAR),
            new ResultSetColumn("external_access_integrations", StringType.VARCHAR)
        );
    }

    public ResultSet showFunctions(final String schemaName, final boolean userOnly) {
        final List<ResultSetColumn> columns = functionColumns();
        final List<Row> rows = new ArrayList<>();

        // User-defined functions in the current (or named) schema first, if a schema is selected.
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName != null && scName != null) {
            appendUserFunctionRows(catalog.getDatabase(dbName).getSchema(scName), dbName, rows);
        }

        // Built-in (system-defined) functions. SHOW FUNCTIONS lists these alongside user functions
        // (is_builtin = 'Y'); SHOW USER FUNCTIONS omits them. Without them SHOW FUNCTIONS returns nothing
        // on a fresh session that has not created any user functions. Live-verified: on a real
        // account SHOW FUNCTIONS = SHOW BUILTIN FUNCTIONS plus the user functions in scope, exactly — the
        // 1134 built-in rows of the two listings share all 926 names, with nothing in either alone.
        if (!userOnly) {
            appendBuiltinFunctionRows(rows);
        }
        return new ResultSet(columns, rows);
    }

    /**
     * SHOW BUILTIN FUNCTIONS: the built-in catalog only, never user-defined functions.
     *
     * <p>Live-verified on a real account: with a schema in use holding one UDF,
     * {@code SHOW BUILTIN FUNCTIONS} returns 1134 rows and {@code SHOW FUNCTIONS} 1135 — the same rows
     * plus that UDF — while {@code SHOW BUILTIN FUNCTIONS LIKE '<the udf>'} returns nothing. The listing
     * ignores scope: {@code IN SCHEMA} / {@code IN DATABASE} / {@code IN ACCOUNT} all still answer 1134.
     */
    public ResultSet showBuiltinFunctions() {
        final List<Row> rows = new ArrayList<>();
        appendBuiltinFunctionRows(rows);
        return new ResultSet(functionColumns(), rows);
    }

    /**
     * One row per name the engine can dispatch, taken from {@code FunctionRegistry.allDispatchableNames()}
     * — the single source shared with the dispatchers themselves.
     *
     * <p>This replaced three loops over the registry's function/aggregate/table-function <em>values</em>,
     * which listed 396 distinct names out of the 552 the engine can run: those loops read
     * {@code BuiltInFunction.getName()}, so every alias reported its canonical name instead (SUBSTR showed
     * as a second SUBSTRING row, and ARRAYAGG, RLIKE, DAYOFMONTH, BIT_OR_AGG, … never appeared at all),
     * and the window, higher-order, SYSTEM$ and operator families are not in those maps to begin with.
     */
    private void appendBuiltinFunctionRows(final List<Row> rows) {
        for (final String name : functionRegistry.allDispatchableNames()) {
            rows.add(builtinRow(name));
        }
    }

    /**
     * SHOW FUNCTIONS IN DATABASE &lt;db&gt;: the built-in catalog plus the user functions of every schema
     * in the database.
     *
     * <p>The built-ins belong here for the same reason they belong in the unqualified listing: scoping
     * the command narrows which <em>user</em> functions it reaches, not whether the system ones are
     * callable. Live-verified on a real account with three UDFs spread over two schemas of
     * one database: {@code SHOW FUNCTIONS} answers 1136 for the schema holding two of them,
     * {@code IN SCHEMA} the other schema 1135, and {@code IN DATABASE} 1137 — 1134 built-ins plus the
     * user functions in scope every time. {@code SHOW USER FUNCTIONS IN DATABASE} answers a bare 3.
     */
    public ResultSet showFunctionsInDatabase(final String databaseName) {
        final List<ResultSetColumn> columns = functionColumns();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(databaseName).getAllSchemas()) {
            appendUserFunctionRows(schema, databaseName, rows);
        }
        appendBuiltinFunctionRows(rows);
        return new ResultSet(columns, rows);
    }

    /** SHOW USER FUNCTIONS IN DATABASE &lt;db&gt;: the same listing with the built-in half dropped. */
    public ResultSet showUserFunctionsInDatabase(final String databaseName) {
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(databaseName).getAllSchemas()) {
            appendUserFunctionRows(schema, databaseName, rows);
        }
        return new ResultSet(functionColumns(), rows);
    }

    private void appendUserFunctionRows(final Schema schema, final String dbName, final List<Row> rows) {
        for (final Function func : schema.getFunctions()) {
            final String sig = func.getName() + buildArgSig(func.getParameters())
                + " RETURN " + func.getReturnType().getName();
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOnText(func.getCreatedTime()),
                func.getName(),
                schema.getName(),
                "N", "N", "N",
                func.getParameters().size(), func.getParameters().size(),
                sig,
                routineDescription(func.getComment(), "user-defined function"),
                dbName,
                func.isTableFunction() ? "Y" : "N",
                "N", "N",
                null, null,
                "N",
                func.getLanguage(),
                "N", "N"
            )));
        }
    }

    /**
     * The SHOW FUNCTIONS / SHOW BUILTIN FUNCTIONS column shape, matched against a real account
     * (both commands, and SHOW USER FUNCTIONS, return the same 20 columns in
     * this order). {@code secrets}, {@code external_access_integrations}, {@code is_memoizable} and
     * {@code is_data_metric} were missing here.
     */
    private List<ResultSetColumn> functionColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("is_builtin", StringType.VARCHAR),
            new ResultSetColumn("is_aggregate", StringType.VARCHAR),
            new ResultSetColumn("is_ansi", StringType.VARCHAR),
            new ResultSetColumn("min_num_arguments", NumericType.INTEGER),
            new ResultSetColumn("max_num_arguments", NumericType.INTEGER),
            new ResultSetColumn("arguments", StringType.VARCHAR),
            new ResultSetColumn("description", StringType.VARCHAR),
            new ResultSetColumn("catalog_name", StringType.VARCHAR),
            new ResultSetColumn("is_table_function", StringType.VARCHAR),
            new ResultSetColumn("valid_for_clustering", StringType.VARCHAR),
            new ResultSetColumn("is_secure", StringType.VARCHAR),
            new ResultSetColumn("secrets", StringType.VARCHAR),
            new ResultSetColumn("external_access_integrations", StringType.VARCHAR),
            new ResultSetColumn("is_external_function", StringType.VARCHAR),
            new ResultSetColumn("language", StringType.VARCHAR),
            new ResultSetColumn("is_memoizable", StringType.VARCHAR),
            new ResultSetColumn("is_data_metric", StringType.VARCHAR)
        );
    }

    /**
     * A SHOW FUNCTIONS row for one dispatchable built-in name (is_builtin = 'Y').
     *
     * <p>The name is the registry <em>key</em>, so an alias is listed under the name it is called by, and
     * the arity / return type come from whichever map holds it. The window, higher-order, SYSTEM$ and
     * operator families have no function object at all, so their arity is left unmodelled (null) — a real
     * account fills those in and reports variadic maxima as -1 (
     * {@code COUNT} is {@code min_num_arguments = 1, max_num_arguments = -1}), which is why a variadic
     * registry function reports -1 here rather than null.
     */
    private Row builtinRow(final String name) {
        final boolean isAggregate = functionRegistry.hasAggregateFunction(name);
        final BuiltInFunction fn = isAggregate
            ? functionRegistry.getAggregateFunction(name) : functionRegistry.getFunction(name);
        final Object minArgs = fn != null ? Integer.valueOf(fn.getMinArgCount()) : null;
        final Object maxArgs = fn == null ? null
            : Integer.valueOf(fn.isVariadic() || fn.getMaxArgCount() < 0 ? -1 : fn.getMaxArgCount());
        final String args;
        if (fn != null) {
            args = name + (fn.getMaxArgCount() == 0 ? "()" : "(...)")
                + " RETURN " + fn.getReturnType().getName();
        } else if (OperatorFunctionNames.contains(name)) {
            // An operator name is not call-shaped — "IS NULL(...)" or "COUNT(*)(...)" would be nonsense.
            args = null;
        } else {
            args = name + "(...)";
        }
        return new Row(Arrays.asList(
            null,                    // created_on
            name,                    // name
            null,                    // schema_name (built-ins are not in a user schema)
            "Y",                     // is_builtin
            isAggregate ? "Y" : "N", // is_aggregate
            "N",                     // is_ansi
            minArgs,                 // min_num_arguments
            maxArgs,                 // max_num_arguments (-1 when variadic)
            args,                    // arguments
            null,                    // description
            null,                    // catalog_name
            functionRegistry.hasTableFunction(name) ? "Y" : "N", // is_table_function
            "N",                     // valid_for_clustering
            "N",                     // is_secure
            null,                    // secrets
            null,                    // external_access_integrations
            "N",                     // is_external_function
            "SQL",                   // language
            "N",                     // is_memoizable
            "N"                      // is_data_metric
        ));
    }

    /**
     * The {@code description} cell of a SHOW FUNCTIONS / SHOW PROCEDURES row for a user routine: its
     * COMMENT, or a fixed placeholder when it has none.
     *
     * <p>Live-verified on a real account: a UDF created without a comment lists
     * {@code description = 'user-defined function'} and a stored procedure without one
     * {@code 'user-defined procedure'}; creating either {@code WITH COMMENT = 'my fn comment'} puts that
     * comment in the same cell instead. The column is therefore never null for a user routine — it was
     * here, because Frostlake passed the raw (null) comment straight through.
     *
     * <p>Built-in rows are a different matter and stay null: a real account fills them with per-function
     * prose ({@code ABS} reads "returns absolute value of numeric"), which only a hand-maintained
     * catalogue of ~1000 strings could reproduce.
     */
    private String routineDescription(final String comment, final String placeholder) {
        return comment == null || comment.isEmpty() ? placeholder : comment;
    }

    private String buildArgSig(final List<Parameter> params) {
        if (params == null || params.isEmpty()) return "()";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getName()).append(" ").append(params.get(i).getDataType().getName());
        }
        return sb.append(")").toString();
    }

    public ResultSet showTags(final String schemaName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) return new ResultSet(tagColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendTagRows(catalog.getDatabase(dbName).getSchema(scName), dbName, rows);
        return new ResultSet(tagColumns(), rows);
    }

    /** SHOW TAGS IN DATABASE &lt;db&gt;: tags across all schemas of the database. */
    public ResultSet showTagsInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(tagColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendTagRows(schema, dbName, rows);
        }
        return new ResultSet(tagColumns(), rows);
    }

    private void appendTagRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final Tag tag : schema.getTags()) {
            final String av = tag.hasAllowedValues() ? String.join(", ", tag.getAllowedValues()) : null;
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOnText(tag.getCreatedTime()),
                tag.getName(),
                dbName, scName,
                tag.getOwner(),
                tag.getComment(),
                av
            )));
        }
    }

    private List<ResultSetColumn> tagColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("allowed_values", StringType.VARCHAR)
        );
    }

    public ResultSet describeTag(final String tagName) {
        List<Row> rows = new ArrayList<>();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );

        Tag tag = catalog.getTag(tagName);
        rows.add(new Row(Arrays.asList("name", tag.getName())));

        if (tag.hasAllowedValues()) {
            rows.add(new Row(Arrays.asList("allowed_values", String.join(", ", tag.getAllowedValues()))));
        }

        rows.add(new Row(Arrays.asList("masking", String.valueOf(tag.isMasking()))));

        if (tag.getComment() != null) {
            rows.add(new Row(Arrays.asList("comment", tag.getComment())));
        }

        return new ResultSet(columns, rows);
    }

    public ResultSet describeFunction(final String name) {
        final Function fn = resolveDescribeSchema().getFunction(lastSegment(name));
        if (fn == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Function", name));
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("signature", routineSignature(fn.getParameters()))));
        rows.add(new Row(Arrays.asList("returns", String.valueOf(fn.getReturnType()))));
        rows.add(new Row(Arrays.asList("language", fn.getLanguage() != null ? fn.getLanguage() : "SQL")));
        if (fn.getNullHandling() != null) {
            rows.add(new Row(Arrays.asList("null handling", fn.getNullHandling())));
        }
        if (fn.getVolatility() != null) {
            rows.add(new Row(Arrays.asList("volatility", fn.getVolatility())));
        }
        if (fn.getBody() != null) {
            rows.add(new Row(Arrays.asList("body", fn.getBody())));
        }
        return propertyValueResult(rows);
    }

    public ResultSet describeProcedure(final String name) {
        final Procedure proc = resolveDescribeSchema().getProcedure(lastSegment(name));
        if (proc == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Procedure", name));
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("signature", routineSignature(proc.getParameters()))));
        rows.add(new Row(Arrays.asList("returns", String.valueOf(proc.getReturnType()))));
        rows.add(new Row(Arrays.asList("language", proc.getLanguage() != null ? proc.getLanguage() : "SQL")));
        if (proc.getExecuteAs() != null) {
            rows.add(new Row(Arrays.asList("execute as", proc.getExecuteAs())));
        }
        if (proc.getBody() != null) {
            rows.add(new Row(Arrays.asList("body", proc.getBody())));
        }
        return propertyValueResult(rows);
    }

    public ResultSet describeMaskingPolicy(final String name) {
        final MaskingPolicy policy = resolveDescribeSchema().getMaskingPolicy(lastSegment(name));
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy", name));
        }
        return policyDescribeResult(policy.getName(), routineSignature(policy.getParameters()),
            normalizePolicyType(policy.getReturnType()), policy.getBody());
    }

    public ResultSet describeRowAccessPolicy(final String name) {
        final RowAccessPolicy policy = resolveDescribeSchema().getRowAccessPolicy(lastSegment(name));
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy", name));
        }
        return policyDescribeResult(policy.getName(), routineSignature(policy.getParameters()),
            "BOOLEAN", policy.getBody());
    }

    /**
     * Snowflake DESC MASKING / ROW ACCESS POLICY returns a single columnar row —
     * name | signature | return_type | body — not property/value rows (live-verified).
     */
    private ResultSet policyDescribeResult(final String name, final String signature,
                                           final String returnType, final String body) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("signature", StringType.VARCHAR),
            new ResultSetColumn("return_type", StringType.VARCHAR),
            new ResultSetColumn("body", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList(name, signature, returnType, body)));
        return new ResultSet(columns, rows);
    }

    /** Snowflake reports policy return types by canonical name: STRING / TEXT surface as VARCHAR. */
    private String normalizePolicyType(final String typeName) {
        if (typeName == null) {
            return null;
        }
        final String upper = typeName.toUpperCase();
        if (upper.equals("STRING") || upper.equals("TEXT")) {
            return "VARCHAR";
        }
        return upper;
    }

    public ResultSet describeFileFormat(final String name) {
        final FileFormat ff = resolveDescribeSchema().getFileFormat(lastSegment(name));
        if (ff == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("File format", name));
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("TYPE", ff.getType())));
        for (final String key : ff.getOptions().keySet()) {
            rows.add(new Row(Arrays.asList(key, ff.getOptions().get(key))));
        }
        // No COMMENT row: live-verified on a real account, DESCRIBE FILE FORMAT lists only
        // the FORMAT properties (TYPE, RECORD_DELIMITER, FIELD_DELIMITER, …) — a format's comment,
        // whether given inline or by COMMENT ON FILE FORMAT, surfaces in SHOW FILE FORMATS instead.
        return propertyValueResult(rows);
    }

    public ResultSet showFileFormats(final String schemaName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) return new ResultSet(fileFormatColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendFileFormatRows(catalog.getDatabase(dbName).getSchema(scName), dbName, rows);
        return new ResultSet(fileFormatColumns(), rows);
    }

    /** SHOW FILE FORMATS IN DATABASE &lt;db&gt;: file formats across all schemas of the database. */
    public ResultSet showFileFormatsInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(fileFormatColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendFileFormatRows(schema, dbName, rows);
        }
        return new ResultSet(fileFormatColumns(), rows);
    }

    private void appendFileFormatRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final FileFormat ff : schema.getFileFormats()) {
            rows.add(new Row(Arrays.asList(
                null,
                ff.getName(),
                dbName, scName,
                ff.getType(),
                null,
                ff.getComment()
            )));
        }
    }

    private List<ResultSetColumn> fileFormatColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR)
        );
    }

    public ResultSet showMaskingPolicies(final String schemaName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) return new ResultSet(policyColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendMaskingPolicyRows(catalog.getDatabase(dbName).getSchema(scName), dbName, rows);
        return new ResultSet(policyColumns(), rows);
    }

    /** SHOW MASKING POLICIES IN DATABASE &lt;db&gt;: masking policies across all schemas of the database. */
    public ResultSet showMaskingPoliciesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(policyColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendMaskingPolicyRows(schema, dbName, rows);
        }
        return new ResultSet(policyColumns(), rows);
    }

    private void appendMaskingPolicyRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final MaskingPolicy mp : schema.getMaskingPolicies()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOnText(mp.getCreatedTime()),
                mp.getName(),
                dbName, scName,
                "MASKING_POLICY",
                mp.getOwner(),
                mp.getComment()
            )));
        }
    }

    public ResultSet showRowAccessPolicies(final String schemaName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) return new ResultSet(policyColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendRowAccessPolicyRows(catalog.getDatabase(dbName).getSchema(scName), dbName, rows);
        return new ResultSet(policyColumns(), rows);
    }

    /** SHOW ROW ACCESS POLICIES IN DATABASE &lt;db&gt;: row-access policies across all schemas of the database. */
    public ResultSet showRowAccessPoliciesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(policyColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendRowAccessPolicyRows(schema, dbName, rows);
        }
        return new ResultSet(policyColumns(), rows);
    }

    private void appendRowAccessPolicyRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final RowAccessPolicy rap : schema.getRowAccessPolicies()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOnText(rap.getCreatedTime()),
                rap.getName(),
                dbName, scName,
                "ROW_ACCESS_POLICY",
                rap.getOwner(),
                rap.getComment()
            )));
        }
    }

    /** Shared column shape for SHOW MASKING POLICIES and SHOW ROW ACCESS POLICIES. */
    private List<ResultSetColumn> policyColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR)
        );
    }

    /** Render a routine/policy parameter list as "(name TYPE, …)". */
    private String routineSignature(final List<Parameter> params) {
        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params.get(i).getName()).append(" ").append(params.get(i).getDataType());
        }
        return sb.append(")").toString();
    }

    /** The simple object name (last segment) of a possibly-qualified name. */
    private String lastSegment(final String name) {
        final int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    /** Current database.schema for describe lookups. */
    private Schema resolveDescribeSchema() {
        return ShowResultHelpers.resolveDescribeSchema(catalog);
    }

    /** Build a two-column (property, value) describe result. */
    private ResultSet propertyValueResult(final List<Row> rows) {
        return ShowResultHelpers.propertyValueResult(rows);
    }
}
