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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code CREATE OR ALTER TABLE} over a table that is already there: the written column list is the shape
 * the table is brought TO, and the rows are kept.
 *
 * <p>What the account allows (each live-verified): a column APPENDED at the end, a column DROPPED from
 * anywhere, NOT NULL set or dropped, a column COMMENT, a VARCHAR widened, a NUMBER's precision changed
 * either way, and the table's own COMMENT and CLUSTER BY.
 *
 * <p>What it refuses, in its own words:
 *
 * <pre>
 *   a column added before the end   Unsupported feature 'CREATE OR ALTER TABLE column add before end of column list'.
 *   the columns re-ordered          Invalid operation column re-ordering is not possible in CREATE OR ALTER TABLE
 *   another type                    cannot change column C from type NUMBER(38,0) to VARCHAR(20)
 *   a VARCHAR narrowed              … because reducing the byte-length of a varchar is not supported.
 *   a NUMBER's scale changed        … because changing the scale of a number is not supported.
 *   a DEFAULT on an existing column Unsupported feature 'Alter Column Set Default'.
 *   a DEFAULT dropped               Dropping default value is not allowed for column 'B' because the column was added after table was created.
 *   a NOT NULL column added         Non-nullable column 'C' cannot be added to non-empty table 'S2' unless it has a non-null default value.
 * </pre>
 *
 * <p>The account applies what it can before it meets a refusal, and says so ("Partial updates may have been
 * applied"); this judges the WHOLE statement first and changes nothing when any part of it is refused, so a
 * refused statement leaves the table exactly as it was.
 */
final class CreateOrAlterTable {

    private CreateOrAlterTable() {
    }

    /**
     * Brings {@code existing} to the written shape.
     *
     * @param existing  the table as it stands
     * @param requested the columns as the statement writes them
     * @param storage   the table's rows, whose presence decides which columns may be added
     * @param queryExecutor      the executor whose shared steps take a dropped column out of every row
     *                           and fill an added one in
     * @param fullyQualifiedName the table, fully qualified
     */
    static void apply(final Table existing, final List<TableColumn> requested, final TableStorage storage,
                      final QueryExecutor queryExecutor, final String fullyQualifiedName) {
        final List<TableColumn> current = new ArrayList<>(existing.getColumns());
        final Set<String> requestedNames = new LinkedHashSet<>();
        for (final TableColumn column : requested) {
            requestedNames.add(column.getName());
        }
        rejectReorderingAndMidAdds(current, requested, requestedNames);
        final long rowCount = storage == null ? 0 : storage.scan().size();
        // Every column's TYPE is judged before any column's default or nullability: a statement that
        // changes one column's type and another's default reports the TYPE (live-verified).
        for (final TableColumn column : requested) {
            final TableColumn before = columnOf(current, column.getName());
            if (before != null) {
                rejectUnalterableType(before, column);
            }
        }
        for (final TableColumn column : requested) {
            final TableColumn before = columnOf(current, column.getName());
            if (before == null) {
                rejectUnaddableColumn(existing, column, rowCount);
            } else {
                rejectUnalterableColumn(before, column);
            }
        }
        // Nothing is refused, so the whole shape can be applied.
        for (final TableColumn column : current) {
            if (!requestedNames.contains(column.getName())) {
                dropColumn(existing, queryExecutor, fullyQualifiedName, column.getName());
            }
        }
        for (final TableColumn column : requested) {
            final TableColumn before = columnOf(current, column.getName());
            if (before == null) {
                existing.addColumn(column);
                queryExecutor.backfillColumn(fullyQualifiedName, existing, column);
            } else {
                final TableColumn retyped = before.retypedCopy(column.getDataType(), column.isNullable());
                retyped.setComment(column.getComment());
                existing.replaceColumn(before.getName(), retyped);
            }
        }
    }

    /** The kept columns must stay in their order, and a new one may only follow them all. */
    private static void rejectReorderingAndMidAdds(final List<TableColumn> current,
                                                   final List<TableColumn> requested,
                                                   final Set<String> requestedNames) {
        final List<String> keptInOrder = new ArrayList<>();
        for (final TableColumn column : current) {
            if (requestedNames.contains(column.getName())) {
                keptInOrder.add(column.getName());
            }
        }
        final List<String> keptAsWritten = new ArrayList<>();
        boolean sawNewColumn = false;
        for (final TableColumn column : requested) {
            final String name = column.getName();
            if (columnOf(current, name) == null) {
                sawNewColumn = true;
                continue;
            }
            if (sawNewColumn) {
                throw new RuntimeException("Unsupported feature"
                    + " 'CREATE OR ALTER TABLE column add before end of column list'.");
            }
            keptAsWritten.add(name);
        }
        if (!keptAsWritten.equals(keptInOrder)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Invalid operation column re-ordering is not possible in CREATE OR ALTER TABLE"));
        }
    }

    /** A column that is not there yet joins at the end, and may not be NOT NULL over rows that exist. */
    private static void rejectUnaddableColumn(final Table existing, final TableColumn added,
                                              final long rowCount) {
        if (!added.isNullable() && added.getDefaultValue() == null && rowCount > 0) {
            throw new RuntimeException(SqlCompilationError.inline("Non-nullable column '"
                + added.getName().toUpperCase(Locale.ROOT) + "' cannot be added to non-empty table '"
                + existing.getName().toUpperCase(Locale.ROOT)
                + "' unless it has a non-null default value."));
        }
    }

    /** What a column that stays may become. */
    private static void rejectUnalterableColumn(final TableColumn before, final TableColumn after) {
        if (after.getDefaultValue() != null && before.getDefaultValue() == null) {
            throw new RuntimeException("Unsupported feature 'Alter Column Set Default'.");
        }
        if (after.getDefaultValue() == null && before.getDefaultValue() != null) {
            throw new RuntimeException("Dropping default value is not allowed for column '"
                + before.getName().toUpperCase(Locale.ROOT)
                + "' because the column was added after table was created.");
        }
    }

    /** What a column that stays may become, by type alone. */
    private static void rejectUnalterableType(final TableColumn before, final TableColumn after) {
        final DataType from = before.getDataType();
        final DataType to = after.getDataType();
        if (from instanceof StringType && to instanceof StringType) {
            if (((StringType) to).getMaxLength() < ((StringType) from).getMaxLength()) {
                throw new RuntimeException(SqlCompilationError.trailing(changeSentence(before, from, to)
                    + " because reducing the byte-length of a varchar is not supported."));
            }
            return;
        }
        if (from instanceof NumericType && to instanceof NumericType) {
            final NumericType was = (NumericType) from;
            final NumericType becomes = (NumericType) to;
            if (was.getScale() != becomes.getScale()) {
                throw new RuntimeException(SqlCompilationError.trailing(changeSentence(before, from, to)
                    + " because changing the scale of a number is not supported."));
            }
            return;
        }
        if (!SqlTypeNames.canonical(from).equals(SqlTypeNames.canonical(to))) {
            throw new RuntimeException(SqlCompilationError.trailing(changeSentence(before, from, to)));
        }
    }

    /** "cannot change column C from type NUMBER(38,0) to VARCHAR(20)". */
    private static String changeSentence(final TableColumn column, final DataType from, final DataType to) {
        return "cannot change column " + column.getName().toUpperCase(Locale.ROOT)
            + " from type " + SqlTypeNames.canonical(from) + " to " + SqlTypeNames.canonical(to);
    }

    /**
     * The column by name, or null. Names match as they resolve, case kept: beside a quoted {@code "x"}, a column
     * written {@code X} is another column, kept, added or dropped on its own (live-verified).
     */
    private static TableColumn columnOf(final List<TableColumn> columns, final String name) {
        for (final TableColumn column : columns) {
            if (column.getName().equals(name)) {
                return column;
            }
        }
        return null;
    }

    /** Drops a column from the table AND from every row, which is what keeps the rows readable. */
    private static void dropColumn(final Table existing, final QueryExecutor queryExecutor,
                                   final String fullyQualifiedName, final String name) {
        int at = -1;
        for (int i = 0; i < existing.getColumns().size(); i++) {
            if (existing.getColumns().get(i).getName().equals(name)) {
                at = i;
                break;
            }
        }
        existing.dropColumn(name);
        if (at >= 0) {
            queryExecutor.dropColumnValues(fullyQualifiedName, at, existing.isTemporary());
        }
    }
}
