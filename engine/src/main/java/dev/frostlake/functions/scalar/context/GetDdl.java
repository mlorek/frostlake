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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET_DDL('<object_type>', '<object_name>' [, <use_fully_qualified_names>])} — reconstructs a
 * {@code CREATE OR REPLACE} statement for an object from the catalog. Supports TABLE, VIEW,
 * MATERIALIZED VIEW and SEQUENCE (the object types the metastore models with enough fidelity to round
 * trip). The optional third argument is accepted for Snowflake compatibility but ignored: names are
 * always emitted unqualified. Unsupported object types raise an error, as does an object that does not
 * exist (the catalog lookup throws).
 */
public class GetDdl extends BuiltInFunction {

    private final Catalog catalog;
    /** Runs a view's defining query to derive its output column names; null until the executor wires it. */
    private final QueryRunner queryRunner;

    public GetDdl(final Catalog catalog) {
        this(catalog, null);
    }

    public GetDdl(final Catalog catalog, final QueryRunner queryRunner) {
        super("GET_DDL", StringType.VARCHAR);
        this.catalog = catalog;
        this.queryRunner = queryRunner;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            throw new RuntimeException("GET_DDL requires a non-null object type and name");
        }
        final String objectType = args.get(0).toString().trim().toUpperCase().replace(' ', '_');
        // The name is a string argument, so it is an identifier reference: fold it the way one resolves.
        final String objectName = SqlIdentifiers.canonicalText(args.get(1).toString().trim());
        switch (objectType) {
            case "TABLE":
                return tableDdl(catalog.resolveTable(objectName));
            case "VIEW":
                return viewDdl(catalog.resolveView(objectName));
            case "MATERIALIZED_VIEW":
                return materializedViewDdl(schemaOf(objectName).getMaterializedView(simpleName(objectName)));
            case "SEQUENCE":
                return sequenceDdl(schemaOf(objectName).getSequence(simpleName(objectName)));
            case "FUNCTION":
                return functionDdl(args.get(1).toString().trim());
            case "PROCEDURE":
                return procedureDdl(args.get(1).toString().trim());
            default:
                throw new RuntimeException("GET_DDL does not support object type: " + objectType);
        }
    }

    // ─────────────────────────── TABLE ───────────────────────────

    private String tableDdl(final Table table) {
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace TABLE ").append(SqlIdentifiers.spellCanonical(table.getName())).append(" (");

        // A UNIQUE constraint spanning several columns is ONE constraint, so it renders as a table-level
        // line; re-emitting it as an inline UNIQUE per column would recreate it as several independent
        // single-column constraints. A single-column UNIQUE keeps the inline form Snowflake prints.
        final List<UniqueConstraint> multiColumnUniques = new ArrayList<UniqueConstraint>();
        for (final UniqueConstraint unique : table.getUniqueConstraints()) {
            if (unique.getColumnNames().size() > 1) {
                multiColumnUniques.add(unique);
            }
        }

        final List<String> lines = new ArrayList<String>();
        for (final TableColumn col : table.getColumns()) {
            lines.add(columnLine(col, spannedByMultiColumnUnique(multiColumnUniques, col.getName())));
        }
        for (final UniqueConstraint unique : multiColumnUniques) {
            lines.add("unique (" + String.join(", ", unique.getColumnNames()) + ")");
        }
        if (!table.getPrimaryKeys().isEmpty()) {
            lines.add("primary key (" + String.join(", ", table.getPrimaryKeys()) + ")");
        }
        for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
            lines.add("foreign key (" + String.join(", ", fk.getColumnNames()) + ") references "
                + fk.getReferencedTable() + " (" + String.join(", ", fk.getReferencedColumns()) + ")");
        }
        // Every CHECK constraint renders as a TABLE-level line, even one written on the column, and in
        // declaration order (live-verified). An explicit name is spelled, a generated one is not.
        for (final CheckConstraint check : table.getCheckConstraints()) {
            lines.add((check.isAutoNamed() ? "" : "constraint " + check.getName() + " ")
                + "check (" + check.getExpression() + ")");
        }
        for (int i = 0; i < lines.size(); i++) {
            sb.append(i == 0 ? "\n\t" : ",\n\t").append(lines.get(i));
        }
        sb.append("\n)");
        if (!table.getClusterKeys().isEmpty()) {
            sb.append(" cluster by (").append(String.join(", ", table.getClusterKeys())).append(")");
        }
        // A join policy renders the same way an aggregation policy does.
        if (table.hasJoinPolicy()) {
            return sb.append(" WITH JOIN POLICY ").append(table.getJoinPolicyName()).append("\n;").toString();
        }
        // An aggregation policy is a TABLE-level attachment: it follows the closing paren, and live
        // puts the semicolon on its own line after it.
        if (table.hasAggregationPolicy()) {
            sb.append(" WITH AGGREGATION POLICY ").append(table.getAggregationPolicyName());
            if (!table.getAggregationEntityKey().isEmpty()) {
                sb.append(" ENTITY KEY (").append(String.join(", ", table.getAggregationEntityKey()))
                  .append(")");
            }
            return sb.append("\n;").toString();
        }
        return sb.append(";").toString();
    }

    /** True when a multi-column UNIQUE already renders the column, so its inline UNIQUE must be skipped. */
    private boolean spannedByMultiColumnUnique(final List<UniqueConstraint> uniques, final String columnName) {
        for (final UniqueConstraint unique : uniques) {
            if (unique.covers(columnName)) {
                return true;
            }
        }
        return false;
    }

    private String columnLine(final TableColumn col, final boolean uniqueRenderedAtTableLevel) {
        final StringBuilder c = new StringBuilder();
        c.append(col.getName()).append(" ").append(renderType(col.getDataType()));
        if (col.getCollation() != null && !col.getCollation().isEmpty()) {
            c.append(" COLLATE '").append(col.getCollation()).append("'");
        }
        if (col.isAutoIncrement()) {
            c.append(" NOT NULL autoincrement start ").append(col.getIdentityStart())
             .append(" increment ").append(col.getIdentityIncrement());
        } else {
            if (!col.isNullable()) {
                c.append(" NOT NULL");
            }
            if (col.getDefaultValue() != null) {
                c.append(" DEFAULT ").append(renderDefault(col.getDefaultValue()));
            }
        }
        if (col.isUnique() && !col.isPrimaryKey() && !uniqueRenderedAtTableLevel) {
            c.append(" UNIQUE");
        }
        // A projection policy renders before a masking one when a column carries both (live-verified).
        if (col.hasProjectionPolicy()) {
            c.append(" WITH PROJECTION POLICY ").append(col.getProjectionPolicyName());
        }
        // A masked column carries its policy here, fully qualified, and always spelled WITH MASKING
        // POLICY even when the column was declared without the WITH. Live puts it after DEFAULT and
        // before COMMENT — and refuses that order reversed, so the position is the measured one.
        if (col.getMaskingPolicyName() != null && !col.getMaskingPolicyName().isEmpty()) {
            c.append(" WITH MASKING POLICY ").append(col.getMaskingPolicyName());
        }
        if (col.getComment() != null && !col.getComment().isEmpty()) {
            c.append(" COMMENT '").append(col.getComment().replace("'", "''")).append("'");
        }
        return c.toString();
    }

    /** Render a column type back to SQL — canonically, exactly as DESCRIBE spells it. */
    private String renderType(final DataType type) {
        return SqlTypeNames.canonical(type);
    }

    /** Render a DEFAULT value: expression text and keyword/number/boolean defaults bare, strings quoted. */
    private String renderDefault(final Object def) {
        return ShowResultHelpers.renderDefaultExpression(def);
    }

    // ─────────────────────────── VIEW ───────────────────────────

    /**
     * Live Snowflake's GET_DDL always renders a view's parenthesized output column list, one
     * tab-indented column per line: {@code create or replace view V(\n\tA,\n\tB\n) as SELECT ...;}.
     * Columns come from the explicit column list when declared, otherwise from executing the
     * defining query; if neither is available the list is omitted (pre-existing shape).
     */
    private String viewDdl(final View view) {
        final List<String> columns = viewOutputColumns(view);
        if (columns == null || columns.isEmpty()) {
            return view.ddl(view.getName());
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace ");
        if (view.isSecure()) {
            sb.append("secure ");
        }
        sb.append("view ").append(view.getName()).append("(");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(i == 0 ? "\n\t" : ",\n\t").append(columns.get(i));
        }
        sb.append("\n) as ");
        return sb.append(SqlObject.withoutTrailingSemicolon(view.getDefinition())).append(";").toString();
    }

    /** The view's output column names: the declared list, else derived by running the definition. */
    private List<String> viewOutputColumns(final View view) {
        if (view.hasExplicitColumnNames()) {
            return view.getColumnNames();
        }
        if (queryRunner == null) {
            return null;
        }
        try {
            final ResultSet rs = queryRunner.runQuery(view.getDefinition());
            if (rs == null) {
                return null;
            }
            final List<String> names = new ArrayList<String>();
            for (final ResultSetColumn column : rs.getColumns()) {
                names.add(column.getName());
            }
            return names;
        } catch (final RuntimeException e) {
            // An unexecutable definition (e.g. a dropped base table) falls back to the list-free form.
            return null;
        }
    }

    private String materializedViewDdl(final MaterializedView view) {
        return view.ddl(view.getName());
    }

    // ─────────────────────────── SEQUENCE ───────────────────────────

    private String sequenceDdl(final Sequence seq) {
        // Live Snowflake wording: "start with N increment by N", not "start N increment N".
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace sequence ").append(seq.getName())
          .append(" start with ").append(seq.getStartValue())
          .append(" increment by ").append(seq.getIncrement())
          .append(seq.isOrder() ? " order" : " noorder");
        if (seq.getComment() != null && !seq.getComment().isEmpty()) {
            sb.append(" comment = '").append(seq.getComment().replace("'", "''")).append("'");
        }
        return sb.append(";").toString();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private Schema schemaOf(final String qualifiedName) {
        final String[] parts = QualifiedName.parse(qualifiedName).parts();
        if (parts.length == 1) {
            return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
        } else if (parts.length == 2) {
            return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
        } else if (parts.length == 3) {
            return catalog.getDatabase(parts[0]).getSchema(parts[1]);
        }
        throw new RuntimeException("Invalid qualified name: " + qualifiedName);
    }

    private String simpleName(final String qualifiedName) {
        final String[] parts = QualifiedName.parse(qualifiedName).parts();
        return parts[parts.length - 1];
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    // ─────────────────────────── FUNCTION / PROCEDURE ───────────────────────────

    /**
     * A routine's DDL, as a real account renders it: the name and every parameter double-quoted, the
     * types canonical, LANGUAGE always spelled even where the CREATE left it out, STRICT and IMMUTABLE
     * each on a line of their own when set, MEMOIZABLE prefixing whatever line follows it, and the body
     * re-quoted as ONE single-quoted literal whatever quoting the CREATE used. CALLED ON NULL INPUT, a
     * NOT NULL return and TEMPORARY are not rendered at all (live-verified).
     */
    private String functionDdl(final String written) {
        final Function function = (Function) routine(written, false);
        final StringBuilder sb = new StringBuilder("CREATE OR REPLACE ");
        if (function.isSecure()) {
            sb.append("SECURE ");
        }
        sb.append("FUNCTION ").append(quotedName(function.getName()))
          .append(routineParameters(function.getParameters())).append("\n");
        sb.append("RETURNS ").append(function.isTableFunction()
            ? "TABLE (" + returnColumns(function.getReturnColumns()) + ")"
            : routineTypeText(function.getReturnType())).append("\n");
        sb.append("LANGUAGE ").append(function.getLanguage().toUpperCase()).append("\n");
        if ("RETURNS NULL ON NULL INPUT".equals(function.getNullHandling())) {
            sb.append("STRICT\n");
        }
        if ("IMMUTABLE".equals(function.getVolatility())) {
            sb.append("IMMUTABLE\n");
        }
        final String memoizable = function.isMemoizable() ? "MEMOIZABLE " : "";
        if (function.getComment() != null && !function.getComment().isEmpty()) {
            sb.append(memoizable).append("COMMENT='").append(function.getComment().replace("'", "''"))
              .append("'\n").append("AS ");
        } else {
            sb.append(memoizable).append("AS ");
        }
        return sb.append(quotedBody(function.getBody())).append(";").toString();
    }

    /** A procedure's DDL, which adds EXECUTE AS where a function has nothing (live-verified). */
    private String procedureDdl(final String written) {
        final Procedure procedure = (Procedure) routine(written, true);
        final StringBuilder sb = new StringBuilder("CREATE OR REPLACE PROCEDURE ");
        sb.append(quotedName(procedure.getName())).append(routineParameters(procedure.getParameters()))
          .append("\n");
        sb.append("RETURNS ").append(procedure.getReturnColumns() != null && !procedure.getReturnColumns().isEmpty()
            ? "TABLE (" + returnColumns(procedure.getReturnColumns()) + ")"
            : routineTypeText(procedure.getReturnType())).append("\n");
        sb.append("LANGUAGE ").append(procedure.getLanguage().toUpperCase()).append("\n");
        sb.append("EXECUTE AS ").append(procedure.getExecuteAs() == null ? "OWNER"
            : procedure.getExecuteAs().toUpperCase()).append("\n");
        if (procedure.getComment() != null && !procedure.getComment().isEmpty()) {
            sb.append("COMMENT='").append(procedure.getComment().replace("'", "''")).append("'\n");
        }
        return sb.append("AS ").append(quotedBody(procedure.getBody())).append(";").toString();
    }

    /**
     * The routine a {@code name(TYPES)} argument names. The name resolves as an identifier reference
     * does, and the overload is chosen by how many types the argument lists; a name nothing holds is
     * refused echoing the argument AS WRITTEN, which is how the account echoes it.
     */
    private Object routine(final String written, final boolean procedure) {
        final int paren = written.indexOf('(');
        final String namePart = paren < 0 ? written : written.substring(0, paren);
        final String argumentPart = paren < 0 ? "" : written.substring(paren + 1).replace(")", "");
        final int arity = argumentPart.trim().isEmpty() ? 0 : argumentPart.split(",").length;
        final String canonical = SqlIdentifiers.canonicalText(namePart.trim());
        final List<Parameter> none = new ArrayList<Parameter>();
        for (final Object overload : procedure
                ? new ArrayList<Object>(schemaOf(canonical).getProcedureOverloads(simpleName(canonical)))
                : new ArrayList<Object>(schemaOf(canonical).getFunctionOverloads(simpleName(canonical)))) {
            final List<Parameter> parameters = overload instanceof Function
                ? ((Function) overload).getParameters() : ((Procedure) overload).getParameters();
            if ((parameters == null ? none : parameters).size() == arity) {
                return overload;
            }
        }
        throw new RuntimeException(SqlCompilationError.of(
            "Object '" + written + "' does not exist or not authorized."));
    }

    /** {@code ("X" NUMBER(38,0) DEFAULT 1, …)} — every parameter quoted, its default kept. */
    private String routineParameters(final List<Parameter> parameters) {
        final StringBuilder out = new StringBuilder("(");
        for (int i = 0; parameters != null && i < parameters.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            final Parameter parameter = parameters.get(i);
            out.append(quotedName(parameter.getName())).append(" ")
               .append(routineTypeText(parameter.getDataType()));
            if (parameter.hasDefault()) {
                out.append(" DEFAULT ").append(parameter.getDefaultValue());
            }
        }
        return out.append(")").toString();
    }

    /** A table return's columns, quoted and typed as the parameters are. */
    private String returnColumns(final List<Parameter> columns) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; columns != null && i < columns.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(quotedName(columns.get(i).getName())).append(" ")
               .append(routineTypeText(columns.get(i).getDataType()));
        }
        return out.toString();
    }

    /**
     * A routine's type as the account spells it: the canonical name, except that a string of the
     * default width is spelled BARE - live renders {@code RETURNS VARCHAR} for a bare declaration and
     * {@code VARCHAR(10)} for a sized one.
     */
    private String routineTypeText(final DataType type) {
        return SqlTypeNames.routineType(type);
    }

    /** The body as one single-quoted literal, its own quotes doubled, whatever quoting the CREATE used. */
    private String quotedBody(final String body) {
        return "'" + (body == null ? "" : body).replace("'", "''") + "'";
    }

    /** A routine's name and parameter names are always double-quoted in its DDL. */
    private String quotedName(final String name) {
        return "\"" + name + "\"";
    }

}
