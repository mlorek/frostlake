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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
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

    public GetDdl(final Catalog catalog) {
        super("GET_DDL", StringType.VARCHAR);
        this.catalog = catalog;
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

        final List<String> lines = new ArrayList<String>();
        for (final TableColumn col : table.getColumns()) {
            lines.add(columnLine(col));
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

    private String columnLine(final TableColumn col) {
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
        if (col.isUnique() && !col.isPrimaryKey()) {
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

    private String viewDdl(final View view) {
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace ");
        if (view.isSecure()) {
            sb.append("secure ");
        }
        sb.append("view ").append(view.getName());
        if (view.hasExplicitColumnNames()) {
            sb.append(" (").append(String.join(", ", view.getColumnNames())).append(")");
        }
        return sb.append(" as ").append(stripTrailingSemicolon(view.getDefinition())).append(";").toString();
    }

    private String materializedViewDdl(final MaterializedView view) {
        return "create or replace materialized view " + view.getName()
            + " as " + stripTrailingSemicolon(view.getDefinition()) + ";";
    }

    // ─────────────────────────── SEQUENCE ───────────────────────────

    private String sequenceDdl(final Sequence seq) {
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace sequence ").append(seq.getName())
          .append(" start ").append(seq.getStartValue())
          .append(" increment ").append(seq.getIncrement())
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

    private static String stripTrailingSemicolon(final String sql) {
        final String trimmed = sql == null ? "" : sql.trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
