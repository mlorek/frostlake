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

package dev.frostlake.system;

import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.VariantType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The columns of INFORMATION_SCHEMA's own views as a real account describes them: each view's columns in order,
 * with the type, the nullability and the description DESCRIBE answers. The descriptions are the account's fixed
 * text, so they are kept as a list rather than derived (live-verified).
 */
public final class InformationSchemaColumns {

    private static final String RESOURCE = "/dev/frostlake/metadata/information-schema-columns.tsv";

    private static volatile Map<String, List<String[]>> columns;

    /** Each view's columns as catalog columns, built once from the list (see {@link #tableColumns}). */
    private static final Map<String, List<TableColumn>> TABLE_COLUMNS = new ConcurrentHashMap<>();

    /** The schema whose views the list describes. */
    private static final String SCHEMA = "INFORMATION_SCHEMA";

    private InformationSchemaColumns() {
    }

    /**
     * The described columns of one view.
     *
     * @param viewName the view's name, as INFORMATION_SCHEMA spells it
     * @return one entry per column, in order, holding the column's name, its type, {@code Y} or {@code N} for
     *         whether it takes NULL, and its description; empty for a name the list does not hold
     */
    public static List<String[]> of(final String viewName) {
        Map<String, List<String[]>> loaded = columns;
        if (loaded == null) {
            loaded = load();
            columns = loaded;
        }
        final List<String[]> described = loaded.get(viewName);
        return described == null ? new ArrayList<String[]>() : described;
    }

    /**
     * A view's columns as SHOW COLUMNS and INFORMATION_SCHEMA.COLUMNS list them: the columns it resolved when it was
     * created, or for one of INFORMATION_SCHEMA's own views, which was never created, the columns the account
     * describes it with (live-verified).
     *
     * @param view       the view
     * @param schemaName the name of the schema holding it
     * @return the columns, or null for a view whose columns never resolved
     */
    public static List<TableColumn> resolvedColumns(final View view, final String schemaName) {
        if (view.getResolvedColumns() != null || !SCHEMA.equals(schemaName)) {
            return view.getResolvedColumns();
        }
        final List<TableColumn> described = tableColumns(view.getName());
        return described.isEmpty() ? null : described;
    }

    /**
     * One of INFORMATION_SCHEMA's own views' columns, each with its type, its nullability and its description as
     * its comment.
     *
     * @param viewName the view's name
     * @return the columns, in order; empty for a name the list does not hold
     */
    public static List<TableColumn> tableColumns(final String viewName) {
        final List<TableColumn> known = TABLE_COLUMNS.get(viewName);
        if (known != null) {
            return known;
        }
        final List<TableColumn> built = new ArrayList<>();
        for (final String[] column : of(viewName)) {
            final TableColumn tableColumn = new TableColumn(column[0], typeOf(column[1]), "Y".equals(column[2]), null,
                false, false, false);
            tableColumn.setComment(column[3]);
            built.add(tableColumn);
        }
        final List<TableColumn> fixed = Collections.unmodifiableList(built);
        TABLE_COLUMNS.put(viewName, fixed);
        return fixed;
    }

    /**
     * The type a column of the list is written with: VARCHAR(n), NUMBER or NUMBER(p,s), TIMESTAMP_LTZ(p), BOOLEAN,
     * VARIANT, OBJECT, ARRAY, or an ARRAY of one of those, its elements perhaps NOT NULL.
     */
    private static DataType typeOf(final String written) {
        final int open = written.indexOf('(');
        final String name = open < 0 ? written : written.substring(0, open);
        final String inside = open < 0 ? "" : written.substring(open + 1, written.lastIndexOf(')'));
        switch (name) {
            case "VARCHAR":
                return new StringType("VARCHAR", Integer.parseInt(inside));
            case "NUMBER":
                if (inside.isEmpty()) {
                    return new NumericType("NUMBER", 38, 0);
                }
                final int comma = inside.indexOf(',');
                return new NumericType("NUMBER", Integer.parseInt(inside.substring(0, comma).trim()),
                    Integer.parseInt(inside.substring(comma + 1).trim()));
            case "TIMESTAMP_LTZ":
                return new DateTimeType("TIMESTAMP_LTZ", Integer.parseInt(inside), true);
            case "BOOLEAN":
                return BooleanType.BOOLEAN;
            case "OBJECT":
                return ObjectType.OBJECT;
            case "ARRAY":
                if (inside.isEmpty()) {
                    return ArrayType.ARRAY;
                }
                final boolean notNull = inside.endsWith(" NOT NULL");
                final String element = notNull ? inside.substring(0, inside.length() - " NOT NULL".length()) : inside;
                return new StructuredArrayType(typeOf(element), notNull);
            default:
                return VariantType.VARIANT;
        }
    }

    private static Map<String, List<String[]>> load() {
        final Map<String, List<String[]>> byView = new HashMap<String, List<String[]>>();
        try (InputStream in = InformationSchemaColumns.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing column list: information schema");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        final String[] cells = line.split("\t", -1);
                        List<String[]> described = byView.get(cells[0]);
                        if (described == null) {
                            described = new ArrayList<String[]>();
                            byView.put(cells[0], described);
                        }
                        described.add(new String[] {cells[1], cells[2], cells[3], cells[4]});
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load column list: information schema", e);
        }
        return byView;
    }
}
