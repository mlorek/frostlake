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
import dev.frostlake.functions.UnlistedFunctionNames;
import dev.frostlake.functions.scalar.context.DefinitionOwnership;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.NullHandling;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.RoutineEnvironment;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SearchOptimizationExpression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.VectorType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SHOW / DESCRIBE handlers for routines and metadata objects: functions, procedures, tags, file formats,
 * masking / row-access policies. Extracted from {@link ShowCommandExecutor}, which delegates here.
 */
final class ShowRoutineExecutor {

    private final Catalog catalog;
    private final FunctionRegistry functionRegistry;
    /** Says whether the session may act as a routine's owner, who alone reads all of its body. */
    private final DefinitionOwnership ownership;

    ShowRoutineExecutor(final Catalog catalog, final FunctionRegistry functionRegistry,
                        final DefinitionOwnership ownership) {
        this.catalog = catalog;
        this.functionRegistry = functionRegistry;
        this.ownership = ownership;
    }

    public ResultSet showProcedures(final String schemaName, final boolean userOnly) {
        final List<ResultSetColumn> columns = procedureColumns();
        final List<Row> rows = new ArrayList<>();

        // User-defined procedures in the current (or named) schema, if one is selected.
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName != null && scName != null) {
            appendUserProcedureRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
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
        for (final Schema schema : catalog.databaseExact(databaseName).getAllSchemas()) {
            appendUserProcedureRows(schema, databaseName, rows);
        }
        appendBuiltinProcedureRows(rows);
        return new ResultSet(columns, rows);
    }

    /** SHOW USER PROCEDURES IN DATABASE &lt;db&gt;: the same listing with the built-in half dropped. */
    public ResultSet showUserProceduresInDatabase(final String databaseName) {
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(databaseName).getAllSchemas()) {
            appendUserProcedureRows(schema, databaseName, rows);
        }
        return new ResultSet(procedureColumns(), rows);
    }

    /**
     * SHOW PROCEDURES with no current database: the user procedures of every schema of every database, each
     * under its own database, then the built-in catalog unless only USER procedures were asked for
     * (live-verified).
     */
    public ResultSet showProceduresInAccount(final boolean userOnly) {
        final List<Row> rows = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                appendUserProcedureRows(schema, database.getName(), rows);
            }
        }
        if (!userOnly) {
            appendBuiltinProcedureRows(rows);
        }
        return new ResultSet(procedureColumns(), rows);
    }

    private void appendUserProcedureRows(final Schema schema, final String dbName, final List<Row> rows) {
        final List<Procedure> procedures = schema.getProcedures();
        Collections.sort(procedures, new Comparator<Procedure>() {
            @Override
            public int compare(final Procedure left, final Procedure right) {
                return listingOrder(left.getName(), left.getParameters(), right.getName(), right.getParameters());
            }
        });
        for (final Procedure proc : procedures) {
            // A table procedure prints its columns, bare type names and all, and is flagged as a table function is.
            final String sig = proc.getName() + listedArgumentsText(proc.getParameters())
                + " RETURN " + (proc.returnsTable() ? "TABLE " + routineSignature(proc.getReturnColumns())
                    : listedType(proc.getReturnType()));
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(proc.getCreatedTime()),
                proc.getName(),
                schema.getName(),
                "N", "N", "N",
                proc.getParameters().size(), proc.getParameters().size(),
                sig,
                routineDescription(proc.getComment(), "user-defined procedure"),
                dbName,
                proc.returnsTable() ? "Y" : "N", "N", proc.isSecure() ? "Y" : "N",
                // Live leaves secrets and external_access_integrations EMPTY, not null, for a routine that
                // names none, as for a function (live-verified).
                proc.getEnvironment().secretsText(dbName, schema.getName()),
                integrationsText(proc.getEnvironment())
            )));
        }
    }

    /**
     * The SHOW PROCEDURES column shape: the first 16 of the 21 {@link #functionColumns()}, which is to
     * say every one of them except the five a procedure has no answer for
     * ({@code is_external_function}, {@code language}, {@code is_memoizable}, {@code is_data_metric},
     * {@code is_ai_function}).
     *
     * <p>Live-verified on a real account: all six procedure listings — plain, USER, BUILTIN,
     * each with and without TERSE — return exactly these 16 names in this order.
     *
     * <p>Frostlake used to end this list with {@code is_external_function, language, execute_as}. The
     * last two are not SHOW PROCEDURES columns at all on a real account; both surface under
     * {@code DESCRIBE PROCEDURE} instead, which answers {@code language | SQL} and
     * {@code execute as | OWNER} property rows (live-verified the same day), and which
     * {@link #describeProcedure(String[], List)} already reports.
     */
    private List<ResultSetColumn> procedureColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("is_builtin", StringType.VARCHAR),
            new ResultSetColumn("is_aggregate", StringType.VARCHAR),
            new ResultSetColumn("is_ansi", StringType.VARCHAR),
            new ResultSetColumn("min_num_arguments", ShowResultHelpers.COUNT),
            new ResultSetColumn("max_num_arguments", ShowResultHelpers.COUNT),
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
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName != null && scName != null) {
            appendUserFunctionRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
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
     * A schema's routines, each as the listing's arguments column names it — {@code F(NUMBER)}, the text before
     * its RETURN — beside its declaration with the parameters' names, {@code F(X NUMBER)}. The names are what
     * tells one schema's routine from another schema's of the same name and argument types, which the listing
     * itself no longer shows.
     *
     * @param schemaName the schema, as a listing's scope names it
     * @param procedures whether the schema's procedures are meant rather than its functions
     * @return the declarations, by listed text
     */
    public Map<String, String> namedDeclarations(final String schemaName, final boolean procedures) {
        final Map<String, String> declarations = new HashMap<>();
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) {
            return declarations;
        }
        final Schema schema = catalog.databaseExact(dbName).schemaExact(scName);
        if (procedures) {
            for (final Procedure procedure : schema.getProcedures()) {
                declarations.put(procedure.getName() + listedArgumentsText(procedure.getParameters()),
                    procedure.getName() + namedArgumentsText(procedure.getParameters()));
            }
        } else {
            for (final Function function : schema.getFunctions()) {
                declarations.put(function.getName() + listedArgumentsText(function.getParameters()),
                    function.getName() + namedArgumentsText(function.getParameters()));
            }
        }
        return declarations;
    }

    /** A signature with each parameter's name before its type, {@code (X NUMBER, Y VARCHAR)}. */
    private static String namedArgumentsText(final List<Parameter> params) {
        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; params != null && i < params.size(); i++) {
            sb.append(i > 0 ? ", " : "").append(params.get(i).getName()).append(' ')
                .append(params.get(i).getDataType().getName());
        }
        return sb.append(")").toString();
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
     * The built-ins the account itself leaves out of the listing ({@link UnlistedFunctionNames}) stay out.
     */
    private void appendBuiltinFunctionRows(final List<Row> rows) {
        for (final String name : functionRegistry.allDispatchableNames()) {
            if (!UnlistedFunctionNames.contains(name)) {
                rows.add(builtinRow(name));
            }
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
        for (final Schema schema : catalog.databaseExact(databaseName).getAllSchemas()) {
            appendUserFunctionRows(schema, databaseName, rows);
        }
        appendBuiltinFunctionRows(rows);
        return new ResultSet(columns, rows);
    }

    /** SHOW USER FUNCTIONS IN DATABASE &lt;db&gt;: the same listing with the built-in half dropped. */
    public ResultSet showUserFunctionsInDatabase(final String databaseName) {
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(databaseName).getAllSchemas()) {
            appendUserFunctionRows(schema, databaseName, rows);
        }
        return new ResultSet(functionColumns(), rows);
    }

    /**
     * SHOW FUNCTIONS with no current database: the user functions of every schema of every database, each
     * under its own database, then the built-in catalog unless only USER functions were asked for
     * (live-verified).
     */
    public ResultSet showFunctionsInAccount(final boolean userOnly) {
        final List<Row> rows = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                appendUserFunctionRows(schema, database.getName(), rows);
            }
        }
        if (!userOnly) {
            appendBuiltinFunctionRows(rows);
        }
        return new ResultSet(functionColumns(), rows);
    }

    private void appendUserFunctionRows(final Schema schema, final String dbName, final List<Row> rows) {
        final List<Function> functions = schema.getFunctions();
        Collections.sort(functions, new Comparator<Function>() {
            @Override
            public int compare(final Function left, final Function right) {
                return listingOrder(left.getName(), left.getParameters(), right.getName(), right.getParameters());
            }
        });
        for (final Function func : functions) {
            final String sig = func.getName() + listedArgumentsText(func.getParameters())
                + " RETURN " + (func.isTableFunction() ? "TABLE " + routineSignature(func.getReturnColumns())
                    : listedType(func.getReturnType()));
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(func.getCreatedTime()),
                func.getName(),
                schema.getName(),
                "N", func.isAggregate() ? "Y" : "N", "N",
                func.getParameters().size(), func.getParameters().size(),
                sig,
                routineDescription(func.getComment(), "user-defined function"),
                dbName,
                func.isTableFunction() ? "Y" : "N",
                "N", func.isSecure() ? "Y" : "N",
                // Live leaves secrets and external_access_integrations EMPTY, not null, for a function that
                // names none (live-verified).
                func.getEnvironment().secretsText(dbName, schema.getName()),
                integrationsText(func.getEnvironment()),
                func.isExternalFunction() ? "Y" : "N",
                func.isExternalFunction() ? "EXTERNAL" : func.getLanguage(),
                func.isMemoizable() ? "Y" : "N", "N", "N"
            )));
        }
    }

    /**
     * The SHOW FUNCTIONS / SHOW BUILTIN FUNCTIONS column shape, matched against a real account
     * (both commands, and SHOW USER FUNCTIONS, return the same 21 columns in
     * this order). {@code secrets}, {@code external_access_integrations}, {@code is_memoizable} and
     * {@code is_data_metric} were missing here; {@code is_ai_function} is the final column and
     * answers N on every builtin of a real account, so it is a constant here.
     */
    private List<ResultSetColumn> functionColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("is_builtin", StringType.VARCHAR),
            new ResultSetColumn("is_aggregate", StringType.VARCHAR),
            new ResultSetColumn("is_ansi", StringType.VARCHAR),
            new ResultSetColumn("min_num_arguments", ShowResultHelpers.COUNT),
            new ResultSetColumn("max_num_arguments", ShowResultHelpers.COUNT),
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
            new ResultSetColumn("is_data_metric", StringType.VARCHAR),
            new ResultSetColumn("is_ai_function", StringType.VARCHAR)
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
            "N",                     // is_data_metric
            "N"                      // is_ai_function
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

    /**
     * The order a schema's routines are listed in: by name, then by argument types, each compared as a
     * binary string, so quoted lower-case names follow every upper-case one — {@code A, B, _z, a, b} — and
     * {@code F()} comes before {@code F(BOOLEAN)}, {@code F(NUMBER)} before {@code F(NUMBER, NUMBER)}
     * (live-verified).
     */
    private static int listingOrder(final String leftName, final List<Parameter> leftParameters,
                                    final String rightName, final List<Parameter> rightParameters) {
        final int byName = leftName.compareTo(rightName);
        return byName != 0 ? byName : argumentTypesText(leftParameters).compareTo(argumentTypesText(rightParameters));
    }

    /**
     * A signature's argument types alone, {@code (NUMBER, VARCHAR)}: what the listings' {@code arguments} column
     * prints after the routine's name, and what orders the overloads of one name. The parameters' names are
     * INFORMATION_SCHEMA's to show ({@code argument_signature}), not SHOW's.
     */
    /**
     * A routine's arguments as its listing's {@code arguments} cell tells them: the types alone, an argument that
     * has a default marked — {@code (NUMBER, DEFAULT VARCHAR)} (live-verified).
     */
    /** The integrations a routine names, as one JSON list; nothing for a routine that names none. */
    private static String integrationsText(final RoutineEnvironment environment) {
        final List<String> names = environment.getExternalAccessIntegrations();
        return names.isEmpty() ? "" : JsonTextList.of(names);
    }

    private static String listedArgumentsText(final List<Parameter> params) {
        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; params != null && i < params.size(); i++) {
            sb.append(i > 0 ? ", " : "").append(params.get(i).hasDefault() ? "DEFAULT " : "")
                .append(listedType(params.get(i).getDataType()));
        }
        return sb.append(")").toString();
    }

    private static String argumentTypesText(final List<Parameter> params) {
        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; params != null && i < params.size(); i++) {
            sb.append(i > 0 ? ", " : "").append(listedType(params.get(i).getDataType()));
        }
        return sb.append(")").toString();
    }

    /**
     * A parameter's type as the listings print it: its bare name, and a structured one with its shape, the types
     * inside it bare too — {@code OBJECT(a NUMBER)}, {@code ARRAY(NUMBER)}, {@code MAP(VARCHAR, NUMBER)}
     * (live-verified). So f(x OBJECT) and f(x OBJECT(a INT)) are listed as the two overloads they are.
     */
    private static String listedType(final DataType type) {
        return StructuredTypes.signatureText(type);
    }

    public ResultSet showTags(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) return new ResultSet(tagColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendTagRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
        return new ResultSet(tagColumns(), rows);
    }

    /** SHOW TAGS IN DATABASE &lt;db&gt;: tags across all schemas of the database. */
    /** SHOW TAGS IN ACCOUNT: every database's tags, in database order. */
    public ResultSet showTagsInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showTagsInDatabase(databaseName);
            }
        });
        return across != null ? across : showTags(null);
    }

    public ResultSet showTagsInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(tagColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(dbName).getAllSchemas()) {
            appendTagRows(schema, dbName, rows);
        }
        return new ResultSet(tagColumns(), rows);
    }

    private void appendTagRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final Tag tag : schema.getTags()) {
            final String av = allowedValuesText(tag);
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(tag.getCreatedTime()),
                tag.getName(),
                dbName, scName,
                tag.getOwner(),
                ShowResultHelpers.text(tag.getComment()),
                av,
                ShowResultHelpers.OWNER_ROLE_TYPE,
                tag.getPropagate() != null ? tag.getPropagate() : "NONE",
                tag.getOnConflict(),
                "false"
            )));
        }
    }

    /** How live spells a tag's ALLOWED_VALUES: a JSON array of the values, or NULL when unset. */
    private static String allowedValuesText(final Tag tag) {
        return tag.allowedValuesText();
    }

    private List<ResultSetColumn> tagColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("allowed_values", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("propagate", StringType.VARCHAR),
            new ResultSetColumn("on_conflict", StringType.VARCHAR),
            new ResultSetColumn("multi_value", StringType.VARCHAR)
        );
    }

    /**
     * DESCRIBE FUNCTION: the overload the written signature picks, by the function's exact canonical name.
     *
     * @param parts         the name's canonical parts
     * @param argumentTypes the argument types written after it
     * @return the property rows
     */
    public ResultSet describeFunction(final String[] parts, final List<DataType> argumentTypes) {
        final Schema owner = routineSchema(parts);
        if (owner == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Function", QualifiedName.join(parts)));
        }
        final Function fn = owner.getFunctionByWrittenSignature(parts[parts.length - 1], argumentTypes);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("signature", describedSignature(fn.getParameters()))));
        // A RETURNS type written NOT NULL says so here, and in no DDL (live-verified).
        rows.add(new Row(Arrays.asList("returns", fn.isTableFunction()
            ? "TABLE " + routineSignature(fn.getReturnColumns())
            : SqlTypeNames.routineType(fn.getReturnType(), fn.getReturnSized())
                + (fn.isReturnNotNull() ? " NOT NULL" : ""))));
        rows.add(new Row(Arrays.asList("language", fn.getLanguage() != null ? fn.getLanguage() : "SQL")));
        if (!isSqlLanguage(fn.getLanguage())) {
            rows.add(new Row(Arrays.asList("null handling", fn.getNullHandling())));
            rows.add(new Row(Arrays.asList("volatility", fn.getVolatility())));
        }
        // A SQL function written without a body describes one all the same, as NULL (live-verified).
        if (fn.getBody() != null || RoutineDescriptionRows.isHandlerLanguage(fn.getLanguage())
                || isSqlLanguage(fn.getLanguage()) && !fn.isServiceFunction()) {
            // A SECURE function describes its body to its owner alone: any other role reads NULL (live-verified).
            rows.add(new Row(Arrays.asList("body",
                fn.isSecure() && !ownership.actsAsOwner(fn.getOwner()) ? null : fn.getBody())));
        }
        RoutineDescriptionRows.add(rows, fn.getLanguage(), fn.getHandler(), fn.getRuntimeVersion(), fn.getImports(),
            fn.getEnvironment(), Boolean.valueOf(fn.isAggregate()));
        if (fn.isServiceFunction()) {
            rows.add(new Row(Arrays.asList("service", fn.getServiceName())));
            rows.add(new Row(Arrays.asList("endpoint", fn.getServiceEndpoint())));
            if (fn.getMaxBatchRows() != null) {
                rows.add(new Row(Arrays.asList("max_batch_rows", fn.getMaxBatchRows().toString())));
            }
        }
        return propertyValueResult(rows);
    }

    /**
     * DESCRIBE PROCEDURE: the overload the written signature picks, by the procedure's exact canonical name.
     *
     * @param parts         the name's canonical parts
     * @param argumentTypes the argument types written after it
     * @return the property rows
     */
    public ResultSet describeProcedure(final String[] parts, final List<DataType> argumentTypes) {
        final Schema owner = routineSchema(parts);
        if (owner == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Procedure", QualifiedName.join(parts)));
        }
        final Procedure proc = owner.getProcedureByWrittenSignature(parts[parts.length - 1], argumentTypes);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("signature", describedSignature(proc.getParameters()))));
        rows.add(new Row(Arrays.asList("returns", proc.returnsTable()
            ? "TABLE " + routineSignature(proc.getReturnColumns())
            : SqlTypeNames.routineType(proc.getReturnType(), proc.getReturnSized())
                + (proc.isReturnNotNull() ? " NOT NULL" : ""))));
        rows.add(new Row(Arrays.asList("language", proc.getLanguage() != null ? proc.getLanguage() : "SQL")));
        if (!isSqlLanguage(proc.getLanguage())) {
            // A procedure declares neither, so the two rows carry the account's own defaults - which is
            // what it answers for one written without them (live-verified on a JavaScript procedure).
            rows.add(new Row(Arrays.asList("null handling", NullHandling.CALLED_ON_NULL_INPUT.getSqlText())));
            rows.add(new Row(Arrays.asList("volatility", "VOLATILE")));
        }
        if (proc.getExecuteAs() != null) {
            rows.add(new Row(Arrays.asList("execute as", proc.getExecuteAs())));
        }
        if (proc.getBody() != null || RoutineDescriptionRows.isHandlerLanguage(proc.getLanguage())) {
            // A procedure's body is its owner's: to any other role a SECURE one describes NULL and a plain
            // one the empty string (live-verified).
            rows.add(new Row(Arrays.asList("body", ownership.actsAsOwner(proc.getOwner()) ? proc.getBody()
                : proc.isSecure() ? null : "")));
        }
        final RoutineEnvironment environment = proc.getEnvironment().copy();
        environment.setPackages(proc.getPackages());
        RoutineDescriptionRows.add(rows, proc.getLanguage(), proc.getHandler(), proc.getRuntimeVersion(),
            proc.getImports(), environment, null);
        return propertyValueResult(rows);
    }

    public ResultSet describeMaskingPolicy(final String name) {
        final Schema home = describedSchema(name);
        // Named exactly: a quoted name in another case is another policy, which is not there (live-verified).
        final MaskingPolicy policy = home.getMaskingPolicyExact(lastSegment(name));
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy", fullPath(home, name)));
        }
        // The return type reads as declared, in the account's own spelling: a DOUBLE reads FLOAT, an INT
        // NUMBER(38,0), and a string or a binary keeps the width it was written with (live-verified).
        final String returns = policy.getReturnSpelling() != null ? policy.getReturnSpelling()
            : policy.getReturnDataType() != null ? SqlTypeNames.routineType(policy.getReturnDataType())
            : normalizePolicyType(policy.getReturnType());
        return policyDescribeResult(policy.getName(), describedSignature(policy.getParameters()), returns,
            policy.getBody());
    }

    public ResultSet describeRowAccessPolicy(final String name) {
        final Schema home = describedSchema(name);
        final RowAccessPolicy policy = home.getRowAccessPolicyExact(lastSegment(name));
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy", fullPath(home, name)));
        }
        return policyDescribeResult(policy.getName(), describedSignature(policy.getParameters()),
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
        final String upper = typeName.toUpperCase(Locale.ROOT);
        if (upper.equals("STRING") || upper.equals("TEXT")) {
            return "VARCHAR";
        }
        return upper;
    }

    /**
     * DESC FILE FORMAT in live's four-column shape: the TYPE's whole property tree with declared
     * options overlaid on the per-type defaults. No COMMENT row — a format's comment surfaces in
     * SHOW FILE FORMATS instead (live-verified).
     */
    public ResultSet describeFileFormat(final String name) {
        final Schema home = describedSchema(name);
        // Named exactly, as a policy is (live-verified).
        final FileFormat ff = home.getFileFormatExact(lastSegment(name));
        if (ff == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("File format", fullPath(home, name)));
        }
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("property_type", StringType.VARCHAR),
            new ResultSetColumn("property_value", StringType.VARCHAR),
            new ResultSetColumn("property_default", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        for (final FormatProperty property
                : FileFormatSurfaces.tree(ff.getType())) {
            String value = ff.getOptions().get(property.name);
            if (value == null) {
                value = "TYPE".equals(property.name) ? ff.getType() : property.valueDefault;
            } else if ("NULL_IF".equals(property.name)) {
                value = "[" + String.join(", ", ff.getNullIfValues()) + "]";
            }
            // A format's default TYPE is CSV whatever its own is, as a stage's is.
            rows.add(new Row(Arrays.asList(property.name, property.type, value,
                "TYPE".equals(property.name) ? "CSV" : property.shownDefault)));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showFileFormats(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) return new ResultSet(fileFormatColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendFileFormatRows(ShowResultHelpers.scopeSchemaReportedGenerically(catalog, dbName, scName), dbName, rows);
        return new ResultSet(fileFormatColumns(), rows);
    }

    /** SHOW FILE FORMATS IN DATABASE &lt;db&gt;: file formats across all schemas of the database. */
    /** SHOW FILE FORMATS IN ACCOUNT: every database's file formats, in database order. */
    public ResultSet showFileFormatsInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showFileFormatsInDatabase(databaseName);
            }
        });
        return across != null ? across : showFileFormats(null);
    }

    public ResultSet showFileFormatsInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(fileFormatColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : ShowResultHelpers.scopeDatabaseReportedGenerically(catalog, dbName)
                .getAllSchemas()) {
            appendFileFormatRows(schema, dbName, rows);
        }
        return new ResultSet(fileFormatColumns(), rows);
    }

    private void appendFileFormatRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final FileFormat ff : schema.getFileFormats()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(ff.getCreatedTime()),
                ff.getName(),
                dbName, scName,
                ff.getType(),
                ff.getOwner(),
                ShowResultHelpers.text(ff.getComment()),
                formatOptionsJson(ff),
                ShowResultHelpers.OWNER_ROLE_TYPE
            )));
        }
    }

    private List<ResultSetColumn> fileFormatColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("format_options", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR)
        );
    }

    /** SHOW FILE FORMATS reports the format's options as a JSON object, TYPE included. */
    /**
     * SHOW FILE FORMATS' format_options blob, live-shaped: the TYPE's WHOLE property tree with
     * typed JSON values — integers and booleans bare, an unset FILE_EXTENSION as null, NULL_IF as
     * a string array — declared options overlaid on the defaults, in tree order.
     */
    private String formatOptionsJson(final FileFormat format) {
        final StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (final FormatProperty property
                : FileFormatSurfaces.tree(format.getType())) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(property.name).append("\":");
            String value = format.getOptions().get(property.name);
            if (value == null) {
                value = "TYPE".equals(property.name) ? format.getType() : property.valueDefault;
            }
            if ("Integer".equals(property.type) || "Long".equals(property.type)
                    || "Boolean".equals(property.type)) {
                json.append(value.isEmpty() ? "null" : value);
            } else if ("List".equals(property.type)) {
                json.append(nullIfJsonArray(format.getNullIfValues(), property.valueDefault));
            } else if ("FILE_EXTENSION".equals(property.name)
                    && format.getOptions().get(property.name) == null) {
                json.append("null");
            } else {
                json.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return json.append('}').toString();
    }

    /** NULL_IF as a JSON string array: the format's own values, or the type's default when it sets none. */
    private static String nullIfJsonArray(final List<String> values, final String valueDefault) {
        if (values == null) {
            return "[]".equals(valueDefault) ? "[]" : "[\"\\\\N\"]";
        }
        final StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                array.append(',');
            }
            array.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return array.append(']').toString();
    }

    public ResultSet showMaskingPolicies(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) return new ResultSet(policyColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendMaskingPolicyRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
        return new ResultSet(policyColumns(), rows);
    }

    /** SHOW MASKING POLICIES IN DATABASE &lt;db&gt;: masking policies across all schemas of the database. */
    /** SHOW MASKING POLICIES IN ACCOUNT: every database's masking policies, in database order. */
    public ResultSet showMaskingPoliciesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showMaskingPoliciesInDatabase(databaseName);
            }
        });
        return across != null ? across : showMaskingPolicies(null);
    }

    public ResultSet showMaskingPoliciesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(policyColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(dbName).getAllSchemas()) {
            appendMaskingPolicyRows(schema, dbName, rows);
        }
        return new ResultSet(policyColumns(), rows);
    }

    private void appendMaskingPolicyRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final MaskingPolicy mp : schema.getMaskingPolicies()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(mp.getCreatedTime()),
                mp.getName(),
                dbName, scName,
                "MASKING_POLICY",
                mp.getOwner(),
                ShowResultHelpers.text(mp.getComment()),
                "ROLE",
                policyOptions(mp.isExemptOtherPolicies())
            )));
        }
    }

    public ResultSet showRowAccessPolicies(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) return new ResultSet(policyColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendRowAccessPolicyRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
        return new ResultSet(policyColumns(), rows);
    }

    /** SHOW ROW ACCESS POLICIES IN DATABASE &lt;db&gt;: row-access policies across all schemas of the database. */
    /** SHOW ROW ACCESS POLICIES IN ACCOUNT: every database's row access policies, in database order. */
    public ResultSet showRowAccessPoliciesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showRowAccessPoliciesInDatabase(databaseName);
            }
        });
        return across != null ? across : showRowAccessPolicies(null);
    }

    public ResultSet showRowAccessPoliciesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(policyColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(dbName).getAllSchemas()) {
            appendRowAccessPolicyRows(schema, dbName, rows);
        }
        return new ResultSet(policyColumns(), rows);
    }

    private void appendRowAccessPolicyRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final RowAccessPolicy rap : schema.getRowAccessPolicies()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(rap.getCreatedTime()),
                rap.getName(),
                dbName, scName,
                "ROW_ACCESS_POLICY",
                rap.getOwner(),
                ShowResultHelpers.text(rap.getComment()),
                "ROLE",
                policyOptions(rap.isExemptOtherPolicies())
            )));
        }
    }

    /** A policy's options cell: {@code {"EXEMPT_OTHER_POLICIES":true}} where it is set, else empty (live-verified). */
    private static String policyOptions(final boolean exempt) {
        return exempt ? "{\"EXEMPT_OTHER_POLICIES\":true}" : "";
    }

    /** Shared column shape for SHOW MASKING POLICIES and SHOW ROW ACCESS POLICIES. */
    /**
     * {@code DESCRIBE SEARCH OPTIMIZATION ON <table>} — the configured expressions, numbered as they
     * were handed out. A table with none answers no rows rather than refusing.
     */
    public ResultSet describeSearchOptimization(final String tableName) {
        final Table table = catalog.resolveTableAsWritten(tableName, "Table");
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("expression_id", NumericType.NUMBER),
            new ResultSetColumn("method", StringType.VARCHAR),
            new ResultSetColumn("target", StringType.VARCHAR),
            new ResultSetColumn("target_data_type", StringType.VARCHAR),
            new ResultSetColumn("active", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        for (final SearchOptimizationExpression expression : table.getSearchOptimization()) {
            rows.add(new Row(Arrays.asList(
                (long) expression.getExpressionId(),
                expression.getMethod(),
                expression.getTarget(),
                expression.getTargetDataType(),
                // Live builds the index asynchronously and reports the build state here; the engine
                // configures instantly, so an expression is active as soon as it exists.
                "true")));
        }
        return new ResultSet(columns, rows);
    }

    /** SHOW JOIN POLICIES — the same shape as the other two constraint-policy kinds. */
    public ResultSet showJoinPolicies(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) {
            return new ResultSet(projectionPolicyColumns(), new ArrayList<>());
        }
        final List<Row> rows = new ArrayList<>();
        appendJoinPolicyRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
        return new ResultSet(projectionPolicyColumns(), rows);
    }

    public ResultSet showJoinPoliciesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) {
            return new ResultSet(projectionPolicyColumns(), new ArrayList<>());
        }
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(dbName).getAllSchemas()) {
            appendJoinPolicyRows(schema, dbName, rows);
        }
        return new ResultSet(projectionPolicyColumns(), rows);
    }

    public ResultSet showJoinPoliciesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showJoinPoliciesInDatabase(databaseName);
            }
        });
        return across != null ? across : showJoinPolicies(null);
    }

    private void appendJoinPolicyRows(final Schema schema, final String dbName, final List<Row> rows) {
        for (final JoinPolicy policy : schema.getJoinPolicies()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(policy.getCreatedTime()),
                policy.getName(),
                dbName, schema.getName(),
                "JOIN_POLICY",
                policy.getOwner(),
                ShowResultHelpers.text(policy.getComment()),
                ShowResultHelpers.OWNER_ROLE_TYPE,
                ""
            )));
        }
    }

    /** DESCRIBE JOIN POLICY — name, empty signature, return type and body. */
    public ResultSet describeJoinPolicy(final String policyName) {
        final JoinPolicy policy = catalog.findJoinPolicy(policyName);
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Join policy",
                catalog.qualifiedObjectName(policyName)));
        }
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("signature", StringType.VARCHAR),
            new ResultSetColumn("return_type", StringType.VARCHAR),
            new ResultSetColumn("body", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList(policy.getName(), "()", "JOIN_CONSTRAINT", policy.getBody())));
        return new ResultSet(columns, rows);
    }

    /** SHOW AGGREGATION POLICIES — the same shape as SHOW PROJECTION POLICIES. */
    public ResultSet showAggregationPolicies(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) {
            return new ResultSet(projectionPolicyColumns(), new ArrayList<>());
        }
        final List<Row> rows = new ArrayList<>();
        appendAggregationPolicyRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
        return new ResultSet(projectionPolicyColumns(), rows);
    }

    public ResultSet showAggregationPoliciesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) {
            return new ResultSet(projectionPolicyColumns(), new ArrayList<>());
        }
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(dbName).getAllSchemas()) {
            appendAggregationPolicyRows(schema, dbName, rows);
        }
        return new ResultSet(projectionPolicyColumns(), rows);
    }

    public ResultSet showAggregationPoliciesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showAggregationPoliciesInDatabase(databaseName);
            }
        });
        return across != null ? across : showAggregationPolicies(null);
    }

    private void appendAggregationPolicyRows(final Schema schema, final String dbName, final List<Row> rows) {
        for (final AggregationPolicy policy : schema.getAggregationPolicies()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(policy.getCreatedTime()),
                policy.getName(),
                dbName, schema.getName(),
                "AGGREGATION_POLICY",
                policy.getOwner(),
                ShowResultHelpers.text(policy.getComment()),
                ShowResultHelpers.OWNER_ROLE_TYPE,
                ""
            )));
        }
    }

    /** DESCRIBE AGGREGATION POLICY — name, empty signature, return type and body. */
    public ResultSet describeAggregationPolicy(final String policyName) {
        final AggregationPolicy policy = catalog.findAggregationPolicyExact(policyName);
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Aggregation policy",
                catalog.qualifiedExactName(policyName)));
        }
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("signature", StringType.VARCHAR),
            new ResultSetColumn("return_type", StringType.VARCHAR),
            new ResultSetColumn("body", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList(policy.getName(), "()", "AGGREGATION_CONSTRAINT", policy.getBody())));
        return new ResultSet(columns, rows);
    }

    /** SHOW PROJECTION POLICIES — the same shape as the other policy kinds, plus an options cell. */
    public ResultSet showProjectionPolicies(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) {
            return new ResultSet(projectionPolicyColumns(), new ArrayList<>());
        }
        final List<Row> rows = new ArrayList<>();
        appendProjectionPolicyRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
        return new ResultSet(projectionPolicyColumns(), rows);
    }

    public ResultSet showProjectionPoliciesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) {
            return new ResultSet(projectionPolicyColumns(), new ArrayList<>());
        }
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(dbName).getAllSchemas()) {
            appendProjectionPolicyRows(schema, dbName, rows);
        }
        return new ResultSet(projectionPolicyColumns(), rows);
    }

    public ResultSet showProjectionPoliciesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showProjectionPoliciesInDatabase(databaseName);
            }
        });
        return across != null ? across : showProjectionPolicies(null);
    }

    private void appendProjectionPolicyRows(final Schema schema, final String dbName, final List<Row> rows) {
        for (final ProjectionPolicy policy : schema.getProjectionPolicies()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(policy.getCreatedTime()),
                policy.getName(),
                dbName, schema.getName(),
                "PROJECTION_POLICY",
                policy.getOwner(),
                ShowResultHelpers.text(policy.getComment()),
                ShowResultHelpers.OWNER_ROLE_TYPE,
                ""
            )));
        }
    }

    private List<ResultSetColumn> projectionPolicyColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("options", StringType.VARCHAR)
        );
    }

    /** DESCRIBE PROJECTION POLICY — one row: the name, its empty signature, return type and body. */
    public ResultSet describeProjectionPolicy(final String policyName) {
        final ProjectionPolicy policy = catalog.findProjectionPolicyExact(policyName);
        if (policy == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Projection policy",
                catalog.qualifiedExactName(policyName)));
        }
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("signature", StringType.VARCHAR),
            new ResultSetColumn("return_type", StringType.VARCHAR),
            new ResultSetColumn("body", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList(policy.getName(), "()", "PROJECTION_CONSTRAINT", policy.getBody())));
        return new ResultSet(columns, rows);
    }

    /** SHOW CONTACTS: its own shape, wider than a policy's — measured against a live account. */
    public ResultSet showContacts(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) return new ResultSet(contactColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendContactRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, rows);
        return new ResultSet(contactColumns(), rows);
    }

    public ResultSet showContactsInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(contactColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(dbName).getAllSchemas()) {
            appendContactRows(schema, dbName, rows);
        }
        return new ResultSet(contactColumns(), rows);
    }

    public ResultSet showContactsInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showContactsInDatabase(databaseName);
            }
        });
        return across != null ? across : showContacts(null);
    }

    private void appendContactRows(final Schema schema, final String dbName, final List<Row> rows) {
        final String scName = schema.getName();
        for (final Contact contact : schema.getContacts()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(contact.getCreatedTime()),
                contact.getName(),
                dbName, scName,
                contact.getOwner(),
                contact.getComment() == null ? "" : contact.getComment(),
                "ROLE",
                contact.getEmailDistributionList(),
                contact.getUrl(),
                // A contact reaches users through its email list; Frostlake models the list itself,
                // so there are no user entries to count and the cells stay at live's empty shape.
                0L,
                null,
                "[]"
            )));
        }
    }

    private List<ResultSetColumn> contactColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("email_distribution_list", StringType.VARCHAR),
            new ResultSetColumn("url", StringType.VARCHAR),
            new ResultSetColumn("entries_in_users", ShowResultHelpers.COUNT),
            new ResultSetColumn("users", StringType.VARCHAR),
            new ResultSetColumn("email_list", StringType.VARCHAR)
        );
    }

    private List<ResultSetColumn> policyColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            // The account's two trailing columns, measured on both listings, TERSE included.
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("options", StringType.VARCHAR)
        );
    }

    /**
     * A function's or a procedure's parameters as DESCRIBE's {@code signature} row tells them: each name and its
     * type, a structured type with its shape and the types inside it bare — {@code (X OBJECT(a NUMBER), Y
     * ARRAY(NUMBER))} — and a VECTOR with its element type and its size (live-verified).
     */
    private static String describedSignature(final List<Parameter> params) {
        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            final DataType type = params.get(i).getDataType();
            sb.append(i > 0 ? ", " : "").append(params.get(i).getName()).append(' ')
                .append(StructuredTypes.isStructured(type) || type instanceof VectorType
                    ? StructuredTypes.signatureText(type) : String.valueOf(type));
        }
        return sb.append(")").toString();
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
        return QualifiedName.parse(name).last();
    }

    /**
     * The schema a routine name belongs to: the one it names when qualified ({@code db..f} names
     * PUBLIC's), else the current one.
     */
    private Schema routineSchema(final String[] written) {
        final String[] parts = catalog.withoutAccount(written, 3);
        if (parts.length == 1) {
            return resolveDescribeSchema();
        }
        final String db = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
        final Database database = db == null ? null : catalog.databaseExact(db);
        return database == null ? null : database.schemaExact(parts[parts.length - 2]);
    }

    /** Current database.schema for describe lookups. */
    private Schema resolveDescribeSchema() {
        return ShowResultHelpers.resolveDescribeSchema(catalog);
    }

    /**
     * The schema a described policy or file format lives in: the one its name gives, which is refused as a
     * schema when it is missing, else the session's.
     */
    private Schema describedSchema(final String name) {
        return QualifiedName.parse(name).size() > 1 ? catalog.resolveOwningSchema(name) : resolveDescribeSchema();
    }

    /** A described object's full path, which a missing one is refused by whatever way its name was written. */
    private String fullPath(final Schema home, final String name) {
        return QualifiedName.join(home.getDatabaseName(), home.getName(), lastSegment(name));
    }

    /** Build a two-column (property, value) describe result. */
    private ResultSet propertyValueResult(final List<Row> rows) {
        return ShowResultHelpers.propertyValueResult(rows);
    }

    /**
     * Whether a routine's language is SQL, which is what decides two of its DESCRIBE rows: null handling
     * and volatility are printed for every OTHER language and for none of SQL's own options - a
     * MEMOIZABLE, IMMUTABLE, STRICT or SECURE SQL function answers the same four rows a plain one does
     * (live-verified).
     */
    private static boolean isSqlLanguage(final String language) {
        return language == null || "SQL".equalsIgnoreCase(language);
    }

}
