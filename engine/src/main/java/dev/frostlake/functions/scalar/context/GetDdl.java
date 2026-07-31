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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
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
        final String objectName = args.get(1).toString().trim();
        switch (objectType) {
            case "TABLE":
                return tableDdl(catalog.resolveTable(objectName));
            case "VIEW":
                return viewDdl(catalog.resolveView(objectName));
            case "MATERIALIZED_VIEW":
                return materializedViewDdl(schemaOf(objectName).getMaterializedView(simpleName(objectName)));
            case "SEQUENCE":
                return sequenceDdl(schemaOf(objectName).getSequence(simpleName(objectName)));
            default:
                throw new RuntimeException("GET_DDL does not support object type: " + objectType);
        }
    }

    // ─────────────────────────── TABLE ───────────────────────────

    private String tableDdl(final Table table) {
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace TABLE ").append(table.getName()).append(" (");

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
        for (int i = 0; i < lines.size(); i++) {
            sb.append(i == 0 ? "\n\t" : ",\n\t").append(lines.get(i));
        }
        sb.append("\n)");
        if (!table.getClusterKeys().isEmpty()) {
            sb.append(" cluster by (").append(String.join(", ", table.getClusterKeys())).append(")");
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
        if (col.getComment() != null && !col.getComment().isEmpty()) {
            c.append(" COMMENT '").append(col.getComment().replace("'", "''")).append("'");
        }
        return c.toString();
    }

    /** Render a column type back to SQL, restoring its length / precision-scale parameters. */
    private String renderType(final DataType type) {
        if (type instanceof NumericType) {
            final NumericType nt = (NumericType) type;
            final String name = nt.getName();
            if (name.equalsIgnoreCase("NUMBER") || name.equalsIgnoreCase("DECIMAL")
                    || name.equalsIgnoreCase("NUMERIC")) {
                return name + "(" + nt.getPrecision() + "," + nt.getScale() + ")";
            }
            return name;
        }
        if (type instanceof StringType) {
            final StringType st = (StringType) type;
            return st.getMaxLength() > 0 ? st.getName() + "(" + st.getMaxLength() + ")" : st.getName();
        }
        return type.getName();
    }

    /** Render a DEFAULT value: expression text and keyword/number/boolean defaults bare, strings quoted. */
    private String renderDefault(final Object def) {
        if (def instanceof DefaultValueExpression || def instanceof Number || def instanceof Boolean) {
            return def.toString();
        }
        final String s = def.toString();
        final String upper = s.toUpperCase();
        if (upper.equals("CURRENT_TIMESTAMP") || upper.equals("CURRENT_DATE") || upper.equals("CURRENT_TIME")
                || upper.equals("TRUE") || upper.equals("FALSE") || upper.equals("NULL") || upper.endsWith("()")) {
            return s;
        }
        return "'" + s.replace("'", "''") + "'";
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

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
