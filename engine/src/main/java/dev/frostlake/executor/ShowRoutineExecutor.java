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

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.TableFunction;
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

        // Built-in system stored procedures (is_builtin = 'Y'). SHOW PROCEDURES lists these alongside
        // user procedures; SHOW USER PROCEDURES omits them. This is the subset of Snowflake's built-in
        // procedures whose behaviour Frostlake implements (its SYSTEM$ task routines), not the full
        // Snowflake catalog.
        if (!userOnly) {
            addBuiltinProcedure(rows, "SYSTEM$WAIT",
                "SYSTEM$WAIT(NUMBER, VARCHAR) RETURN VARCHAR",
                "Pause the current session for the given number of seconds (or the given time unit).");
            addBuiltinProcedure(rows, "SYSTEM$SET_RETURN_VALUE",
                "SYSTEM$SET_RETURN_VALUE(VARCHAR) RETURN VARCHAR",
                "Set the return value of the current task run, readable by its successors.");
            addBuiltinProcedure(rows, "SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS",
                "SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(VARCHAR) RETURN VARCHAR",
                "Cancel all ongoing executions of the specified task.");
        }
        return new ResultSet(columns, rows);
    }

    /** SHOW PROCEDURES IN DATABASE &lt;db&gt;: user procedures across all schemas of the database. Built-in
     *  procedures are not scoped to a user database, so they are omitted here. */
    public ResultSet showProceduresInDatabase(final String databaseName) {
        final List<ResultSetColumn> columns = procedureColumns();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(databaseName).getAllSchemas()) {
            appendUserProcedureRows(schema, databaseName, rows);
        }
        return new ResultSet(columns, rows);
    }

    private void appendUserProcedureRows(final Schema schema, final String dbName, final List<Row> rows) {
        for (final Procedure proc : schema.getProcedures()) {
            final String sig = proc.getName() + buildArgSig(proc.getParameters())
                + " RETURN " + proc.getReturnType().getName();
            rows.add(new Row(Arrays.asList(
                proc.getCreatedTime().toString(),
                proc.getName(),
                schema.getName(),
                "N", "N", "N",
                proc.getParameters().size(), proc.getParameters().size(),
                sig,
                proc.getComment(),
                dbName,
                "N", "N", "N", "N",
                proc.getLanguage(),
                proc.getExecuteAs()
            )));
        }
    }

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
            new ResultSetColumn("is_external_function", StringType.VARCHAR),
            new ResultSetColumn("language", StringType.VARCHAR),
            new ResultSetColumn("execute_as", StringType.VARCHAR)
        );
    }

    /** Append a SHOW PROCEDURES row for a built-in system stored procedure (is_builtin = 'Y'). */
    private void addBuiltinProcedure(final List<Row> rows, final String name, final String arguments,
                                     final String description) {
        rows.add(new Row(Arrays.asList(
            null,              // created_on
            name,              // name
            null,              // schema_name (built-ins are not in a user schema)
            "Y",               // is_builtin
            "N",               // is_aggregate
            "N",               // is_ansi
            null, null,        // min / max num arguments (not modeled)
            arguments,         // arguments
            description,       // description
            null,              // catalog_name
            "N", "N", "N", "N", // is_table_function / valid_for_clustering / is_secure / is_external_function
            "SQL",             // language
            "OWNER"            // execute_as
        )));
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

        // Built-in (system-defined) functions: scalars, aggregates, and table functions. SHOW FUNCTIONS
        // lists these alongside user functions (is_builtin = 'Y'); SHOW USER FUNCTIONS omits them. Without
        // them SHOW FUNCTIONS returns nothing on a fresh session that has not created any user functions.
        if (!userOnly) {
            for (final BuiltInFunction fn : functionRegistry.getAllFunctions()) {
                rows.add(builtinRow(fn, "N", "N"));
            }
            for (final AggregateFunction fn : functionRegistry.getAllAggregateFunctions()) {
                rows.add(builtinRow(fn, "Y", "N"));
            }
            for (final TableFunction fn : functionRegistry.getAllTableFunctions()) {
                rows.add(builtinTableFunctionRow(fn));
            }
        }
        return new ResultSet(columns, rows);
    }

    /** SHOW FUNCTIONS IN DATABASE &lt;db&gt;: user functions across all schemas of the database. Built-in
     *  functions are not scoped to a user database, so they are omitted here. */
    public ResultSet showFunctionsInDatabase(final String databaseName) {
        final List<ResultSetColumn> columns = functionColumns();
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(databaseName).getAllSchemas()) {
            appendUserFunctionRows(schema, databaseName, rows);
        }
        return new ResultSet(columns, rows);
    }

    private void appendUserFunctionRows(final Schema schema, final String dbName, final List<Row> rows) {
        for (final Function func : schema.getFunctions()) {
            final String sig = func.getName() + buildArgSig(func.getParameters())
                + " RETURN " + func.getReturnType().getName();
            rows.add(new Row(Arrays.asList(
                func.getCreatedTime().toString(),
                func.getName(),
                schema.getName(),
                "N", "N", "N",
                func.getParameters().size(), func.getParameters().size(),
                sig,
                func.getComment(),
                dbName,
                func.isTableFunction() ? "Y" : "N",
                "N", "N", "N",
                func.getLanguage()
            )));
        }
    }

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
            new ResultSetColumn("is_external_function", StringType.VARCHAR),
            new ResultSetColumn("language", StringType.VARCHAR)
        );
    }

    /** A SHOW FUNCTIONS row for a built-in scalar / aggregate function (is_builtin = 'Y'). */
    private Row builtinRow(final BuiltInFunction fn, final String isAggregate, final String isTableFunction) {
        final Object maxArgs = fn.isVariadic() ? null : fn.getMaxArgCount();
        final String args = fn.getName() + (fn.getMaxArgCount() == 0 ? "()" : "(...)")
            + " RETURN " + fn.getReturnType().getName();
        return new Row(Arrays.asList(
            null,                    // created_on
            fn.getName(),            // name
            null,                    // schema_name (built-ins are not in a user schema)
            "Y",                     // is_builtin
            isAggregate,             // is_aggregate
            "N",                     // is_ansi
            fn.getMinArgCount(),     // min_num_arguments
            maxArgs,                 // max_num_arguments (null when variadic)
            args,                    // arguments
            null,                    // description
            null,                    // catalog_name
            isTableFunction,         // is_table_function
            "N",                     // valid_for_clustering
            "N",                     // is_secure
            "N",                     // is_external_function
            "SQL"                    // language
        ));
    }

    /** A SHOW FUNCTIONS row for a built-in table function (per-argument metadata is not modeled). */
    private Row builtinTableFunctionRow(final TableFunction fn) {
        return new Row(Arrays.asList(
            null, fn.getName(), null,
            "Y", "N", "N",
            null, null,
            fn.getName() + "(...)",
            null, null,
            "Y",
            "N", "N", "N",
            "SQL"
        ));
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
                tag.getCreatedTime().toString(),
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
            throw new RuntimeException("Function does not exist: " + name);
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
            throw new RuntimeException("Procedure does not exist: " + name);
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
            throw new RuntimeException("Masking policy does not exist: " + name);
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("name", lastSegment(name))));
        rows.add(new Row(Arrays.asList("signature", routineSignature(policy.getParameters()))));
        rows.add(new Row(Arrays.asList("return_type", policy.getReturnType())));
        rows.add(new Row(Arrays.asList("body", policy.getBody())));
        return propertyValueResult(rows);
    }

    public ResultSet describeRowAccessPolicy(final String name) {
        final RowAccessPolicy policy = resolveDescribeSchema().getRowAccessPolicy(lastSegment(name));
        if (policy == null) {
            throw new RuntimeException("Row access policy does not exist: " + name);
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("name", lastSegment(name))));
        rows.add(new Row(Arrays.asList("signature", routineSignature(policy.getParameters()))));
        rows.add(new Row(Arrays.asList("return_type", "BOOLEAN")));
        rows.add(new Row(Arrays.asList("body", policy.getBody())));
        return propertyValueResult(rows);
    }

    public ResultSet describeFileFormat(final String name) {
        final FileFormat ff = resolveDescribeSchema().getFileFormat(lastSegment(name));
        if (ff == null) {
            throw new RuntimeException("File format does not exist: " + name);
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("TYPE", ff.getType())));
        for (final String key : ff.getOptions().keySet()) {
            rows.add(new Row(Arrays.asList(key, ff.getOptions().get(key))));
        }
        if (ff.getComment() != null) {
            rows.add(new Row(Arrays.asList("COMMENT", ff.getComment())));
        }
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
                mp.getCreatedTime().toString(),
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
                rap.getCreatedTime().toString(),
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
