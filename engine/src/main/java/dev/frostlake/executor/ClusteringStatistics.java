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

import dev.frostlake.executor.expressions.ColumnReferenceCollectWalk;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.StorageEngine;

import java.util.List;
import java.util.Locale;

/**
 * SYSTEM$CLUSTERING_DEPTH, SYSTEM$CLUSTERING_INFORMATION and SYSTEM$CLUSTERING_RATIO over a table this engine
 * keeps in one partition (live-verified, for a table of a few rows and for an empty one).
 *
 * <p>The first argument names a table; the second, when it is a list in parentheses, the keys to answer for in
 * place of the table's own. A table with no key and no such list is "Invalid clustering keys or table T is not
 * clustered". A key that names no column of the table is an invalid identifier, placed where the account's own
 * statement holds the list: forty-two characters in, on line one, wherever the call stands. A second argument that
 * is no such list — a bare name, an empty text, NULL, a number — is not read. SYSTEM$CLUSTERING_DEPTH's third
 * argument is a predicate the rows are filtered by, compiled in a statement of the account's own after the table's
 * name and the key list, the table's own when none is given: a fault in it is placed fifty-six characters in, after
 * both as written, and a predicate no row meets gives the depth of no partition.
 *
 * <pre>
 *   SYSTEM$CLUSTERING_DEPTH('t')              1.0, and 0.0 for a table with no row
 *   SYSTEM$CLUSTERING_INFORMATION('t')        the report below, cluster_by_keys the list as written after LINEAR
 *   SYSTEM$CLUSTERING_RATIO('t')              Unsupported feature 'Clustering Ratio'.
 *   … (NULL)                                  null argument for table name
 *   … ('a_view')                              Cannot specify clustering keys on views
 * </pre>
 */
public final class ClusteringStatistics {

    /** Where the key list starts in the statement the account compiles it in. */
    private static final int KEY_LIST_COLUMN = 42;

    /** Where a predicate starts in the statement the account compiles it in, the table's and the keys' text aside. */
    private static final int PREDICATE_COLUMN = 56;

    /** What the predicate follows in the statement it is compiled in here. */
    private static final String WHERE = " WHERE ";

    /** The report of SYSTEM$CLUSTERING_INFORMATION: its keys, its partition count and its depth. */
    private static final String REPORT = """
        {
          "cluster_by_keys" : "LINEAR%s",
          "total_partition_count" : %d,
          "total_constant_partition_count" : 0,
          "average_overlaps" : 0.0,
          "average_depth" : %s,
          "version" : "OPTIMA",
          "clustering_errors" : [ ]
        }""";

    private final String table;
    private final String keys;
    private final boolean populated;

    private ClusteringStatistics(final String table, final String keys, final boolean populated) {
        this.table = table;
        this.keys = keys;
        this.populated = populated;
    }

    /**
     * SYSTEM$CLUSTERING_DEPTH: the average depth, which one partition makes 1 and no partition 0.
     *
     * @param catalog the catalog
     * @param storage the storage that tells whether the table holds a row
     * @param args    the call's arguments
     * @param runner  what runs the query a predicate is compiled in
     * @return the depth
     */
    public static Double depth(final Catalog catalog, final StorageEngine storage, final List<Object> args,
                               final QueryRunner runner) {
        final ClusteringStatistics read = read(catalog, storage, args, false);
        final boolean filtered = args.size() > 2 && args.get(2) != null && runner != null;
        return Double.valueOf((filtered ? read.meets(String.valueOf(args.get(2)), runner) : read.populated) ? 1.0
            : 0.0);
    }

    /** Whether a row of the table meets the predicate, compiled where the account compiles it. */
    private boolean meets(final String predicate, final QueryRunner runner) {
        final StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table);
        final int column = PREDICATE_COLUMN + table.length() + keys.length();
        while (sql.length() < column - WHERE.length()) {
            sql.append(' ');
        }
        sql.append(WHERE).append(predicate);
        // A statement of its own, placed from its own first character, and one query and nothing else: a statement
        // after a semicolon is refused as TO_QUERY refuses one.
        final ResultSet counted;
        final SourcePosition displaced = ExpressionSource.begin(null);
        try {
            counted = runner.runQuery(ToQueryText.requireQuery(sql.toString()));
        } finally {
            ExpressionSource.end(displaced);
        }
        final Object rows = counted == null || counted.getRows().isEmpty() ? null
            : counted.getRows().get(0).getValue(0);
        return rows instanceof Number && ((Number) rows).longValue() > 0;
    }

    /**
     * SYSTEM$CLUSTERING_INFORMATION: the report, as text.
     *
     * @param catalog the catalog
     * @param storage the storage that tells whether the table holds a row
     * @param args    the call's arguments
     * @return the report
     */
    public static String information(final Catalog catalog, final StorageEngine storage, final List<Object> args) {
        final ClusteringStatistics read = read(catalog, storage, args, true);
        final String keys = read.keys.replace("\\", "\\\\").replace("\"", "\\\"");
        return String.format(Locale.ROOT, REPORT, keys, Integer.valueOf(read.populated ? 1 : 0),
            read.populated ? "1.0" : "0.0");
    }

    /**
     * SYSTEM$CLUSTERING_RATIO: refused as the feature it no longer is, once its arguments have been read.
     *
     * @param catalog the catalog
     * @param storage the storage
     * @param args    the call's arguments
     * @return never
     */
    public static Object ratio(final Catalog catalog, final StorageEngine storage, final List<Object> args) {
        read(catalog, storage, args, false);
        throw new RuntimeException("Unsupported feature 'Clustering Ratio'.");
    }

    private static ClusteringStatistics read(final Catalog catalog, final StorageEngine storage,
                                             final List<Object> args, final boolean numberNamesInFull) {
        final Object named = args.isEmpty() ? null : args.get(0);
        if (named == null) {
            throw new RuntimeException("null argument for table name");
        }
        final String writtenName = named.toString().replaceAll("^'|'$", "");
        final String[] parts = SqlIdentifiers.canonicalTextParts(writtenName.trim());
        if (parts.length > 3) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final Schema home = catalog.resolveOwningSchema(QualifiedName.join(parts));
        final String name = parts.length == 0 ? "" : parts[parts.length - 1];
        if (home.hasView(name)) {
            throw new RuntimeException("Cannot specify clustering keys on views\n");
        }
        // The name is matched as an identifier is: a quoted "t" is no table T.
        if (!home.hasTable(name) || !home.getTable(name).getName().equals(name)) {
            // A number is read as a name in full; a text is told as it was written.
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table",
                numberNamesInFull && named instanceof Number ? home.qualifiedName(name) : QualifiedName.join(parts)));
        }
        final Table table = home.getTable(name);
        final String written = args.size() > 1 ? keyList(args.get(1)) : null;
        if (written != null) {
            requireColumns(table, written);
        }
        final String keys = written != null ? written : ownKeys(table);
        if (keys == null) {
            throw new RuntimeException("Invalid clustering keys or table " + table.getName() + " is not clustered");
        }
        final boolean populated = storage != null && storage.getTableStorage(
            QualifiedName.key(home.getDatabaseName(), home.getName(), table.getName())).getRowCount() > 0;
        return new ClusteringStatistics(writtenName, keys, populated);
    }

    /** The second argument when it is a list in parentheses that holds something, as written; null otherwise. */
    private static String keyList(final Object argument) {
        if (!(argument instanceof String)) {
            return null;
        }
        final String text = (String) argument;
        final String trimmed = text.trim();
        return trimmed.length() > 2 && trimmed.startsWith("(") && trimmed.endsWith(")")
            && !trimmed.substring(1, trimmed.length() - 1).isBlank() ? text : null;
    }

    /** The table's own key as a list in parentheses, the word LINEAR before it dropped; null when it has none. */
    private static String ownKeys(final Table table) {
        final String text = table.getClusterByText();
        if (text == null) {
            return null;
        }
        final int open = text.indexOf('(');
        return open < 0 ? "(" + text + ")" : text.substring(open);
    }

    /** Refuses a key list that names a column the table does not have, at the name. */
    private static void requireColumns(final Table table, final String keys) {
        final Expression list;
        try {
            list = ExpressionEvaluator.parse(keys);
        } catch (final RuntimeException notRead) {
            return;
        }
        final ColumnReferenceCollectWalk names = new ColumnReferenceCollectWalk();
        list.accept(names);
        for (final ColumnReferenceExpression column : names.references()) {
            if (!column.isQualified() && !table.hasColumn(column.getColumnName())) {
                final int within = column.getPosition() == null ? 0 : column.getPosition().getCharPositionInLine();
                throw new RuntimeException(SqlCompilationError.invalidIdentifier(1, KEY_LIST_COLUMN + within,
                    column.getWrittenName() != null ? column.getWrittenName()
                        : column.getColumnName().toUpperCase(Locale.ROOT)));
            }
        }
    }
}
