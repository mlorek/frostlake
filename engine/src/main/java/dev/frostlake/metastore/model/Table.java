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

package dev.frostlake.metastore.model;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.types.DataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Table extends SqlObject {

    private final List<TableColumn> columns;
    private final List<String> primaryKeys;
    private final List<ForeignKeyConstraint> foreignKeys;
    private final Map<String, Integer> columnIndex;
    private final boolean isTemporary;
    private final boolean isTransient;
    // Declared with CREATE HYBRID TABLE. Frostlake stores hybrid tables as ordinary tables (row storage,
    // constraints not specially enforced); this flag only preserves the declaration for SHOW HYBRID TABLES
    // and the reported kind.
    private boolean hybrid;
    private long rowCount;
    private List<String> clusterKeys;
    private String rowAccessPolicyName;  // qualified name of attached row access policy, or null
    private List<String> rowAccessPolicyColumns = new ArrayList<>();  // columns passed to policy
    // Names for the constraints this table carries as column flags rather than as constraint objects: the
    // PRIMARY KEY (one constraint spanning every PK column) and the per-column UNIQUE / inline-REFERENCES
    // constraints. Either the name an explicit CONSTRAINT <name> clause gave, or an auto-generated
    // SYS_CONSTRAINT_<uuid> (see ConstraintNames) produced on first request and kept until the constraint
    // is dropped, so every metadata surface reports the same name.
    private String primaryKeyConstraintName;
    private final Map<String, String> uniqueConstraintNames = new HashMap<>();
    private final Map<String, String> columnForeignKeyConstraintNames = new HashMap<>();
    // UNIQUE constraints declared at TABLE level — the only ones whose name or multi-column span the
    // per-column flags cannot express. A bare column-level UNIQUE has no entry here; getUniqueConstraints()
    // synthesizes its single-column constraint from the flag.
    private final List<UniqueConstraint> uniqueConstraints = new ArrayList<>();
    // Whether this instance is the CATALOG's own object for its name — see isCatalogResident().
    private boolean catalogResident;
    // Set only on a USING / NATURAL join's merged relation: the join-key column names (upper-cased,
    // in USING-list / left-table order). SELECT * surfaces these first, and a bare reference to one
    // reads the first NON-NULL of its per-side copies — the merged column of an outer join. Null for
    // every ordinary table.
    private List<String> joinKeyNames;

    public Table(final String name, final List<TableColumn> columns, final boolean isTemporary) {
        this(name, columns, isTemporary, false);
    }

    /**
     * Whether this instance is a real table the catalog holds, as opposed to one of the many derived
     * relations the executor builds and hands round in the same shape — a CTE result, a VALUES list, a
     * subquery, a merged join view, a self-join copy, a stage scan, a table function's output.
     *
     * <p>Only a schema registering the table sets this ({@code Schema.addTable}), so it answers by
     * IDENTITY and never by name. Asking the catalog to re-resolve the table's bare name instead — what
     * the type channel used to do — silently answered "not a catalog table" for every table reached
     * through a schema-qualified {@code FROM}, because a bare name resolves in the session's CURRENT
     * schema: {@code FROM base.t} while sitting in PUBLIC resolved nothing, and where PUBLIC happened
     * to hold its own {@code t} it resolved the wrong instance. Every column of such a table was then
     * treated as having no declared type, which is why a view over one reported the VARCHAR
     * placeholder for expressions whose type is perfectly well known.
     */
    public boolean isCatalogResident() {
        return catalogResident;
    }

    /** Called by {@code Schema.addTable} as the table enters the catalog. One-way on purpose: a
     *  dropped table is unreachable from the query path, and a rename re-registers the same instance. */
    public void markCatalogResident() {
        this.catalogResident = true;
    }

    /** The join-key column names of a USING / NATURAL join's merged relation (upper-cased, in
     *  USING-list / left-table order), or null for an ordinary table. */
    public List<String> getJoinKeyNames() {
        return joinKeyNames;
    }

    public void setJoinKeyNames(final List<String> joinKeyNames) {
        this.joinKeyNames = joinKeyNames;
    }

    public Table(final String name, final List<TableColumn> columns, final boolean isTemporary, final boolean isTransient) {
        super(name);
        this.columns = new ArrayList<>(columns);
        this.primaryKeys = new ArrayList<>();
        this.foreignKeys = new ArrayList<>();
        this.columnIndex = new HashMap<>();
        this.isTemporary = isTemporary;
        this.isTransient = isTransient;
        this.rowCount = 0;
        this.clusterKeys = new ArrayList<>();

        // Build column index
        for (int i = 0; i < columns.size(); i++) {
            columnIndex.put(columns.get(i).getName().toUpperCase(), i);
            if (columns.get(i).isPrimaryKey()) {
                primaryKeys.add(columns.get(i).getName());
            }
        }
    }

    public List<TableColumn> getColumns() {
        return new ArrayList<>(columns);
    }

    /**
     * The column, or null when there is none — for callers that phrase the miss their own way. Resolves
     * exactly as {@link #getColumn} does, through the same index, so the two can never disagree.
     */
    public TableColumn findColumn(final String name) {
        final Integer index = columnIndex.get(name.toUpperCase());
        return index == null ? null : columns.get(index);
    }

    public TableColumn getColumn(final String name) {
        Integer index = columnIndex.get(name.toUpperCase());
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(name));
        }
        return columns.get(index);
    }

    public int getColumnIndex(final String name) {
        Integer index = columnIndex.get(name.toUpperCase());
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(name));
        }
        return index;
    }

    public boolean hasColumn(final String name) {
        return columnIndex.containsKey(name.toUpperCase());
    }

    public List<String> getPrimaryKeys() {
        return new ArrayList<>(primaryKeys);
    }

    public List<ForeignKeyConstraint> getForeignKeys() {
        return new ArrayList<>(foreignKeys);
    }

    /**
     * The name of this table's PRIMARY KEY constraint — one constraint however many columns it spans —
     * or null when the table has none: the name an explicit {@code CONSTRAINT <name> PRIMARY KEY} gave it
     * (see {@link #setPrimaryKeyConstraintName}), else an auto-generated {@code SYS_CONSTRAINT_<uuid>}.
     */
    public String primaryKeyConstraintName() {
        if (!hasPrimaryKeyColumn()) {
            return null;
        }
        if (primaryKeyConstraintName == null) {
            primaryKeyConstraintName = ConstraintNames.generate();
        }
        return primaryKeyConstraintName;
    }

    /**
     * Record the name an explicit {@code CONSTRAINT <name> PRIMARY KEY} declaration gave this table's
     * primary key. A null or blank name changes nothing, leaving the constraint to auto-name itself.
     */
    public void setPrimaryKeyConstraintName(final String name) {
        if (name != null && !name.isEmpty()) {
            primaryKeyConstraintName = name;
        }
    }

    /**
     * The name of the single-column UNIQUE constraint a column-level {@code UNIQUE} declares. A column that
     * belongs to a table-level constraint is named by that constraint instead — see
     * {@link #getUniqueConstraints()}.
     */
    public String uniqueConstraintName(final String columnName) {
        return memoizedConstraintName(uniqueConstraintNames, columnName);
    }

    /** Restore a persisted name for the single-column UNIQUE constraint on one column. */
    public void setUniqueConstraintName(final String columnName, final String name) {
        if (name != null && !name.isEmpty()) {
            uniqueConstraintNames.put(columnName.toUpperCase(), name);
        }
    }

    /** Restore a persisted name for the FOREIGN KEY constraint an inline {@code REFERENCES} declares. */
    public void setColumnForeignKeyConstraintName(final String columnName, final String name) {
        if (name != null && !name.isEmpty()) {
            columnForeignKeyConstraintNames.put(columnName.toUpperCase(), name);
        }
    }

    /**
     * Every UNIQUE constraint on this table, ONE entry per constraint (never one per column): the
     * table-level constraints as declared — a {@code UNIQUE (a, b)} is a single constraint with a single
     * name — followed by a single-column constraint for every unique column none of them spans, which is
     * what a bare column-level {@code UNIQUE} declares.
     */
    public List<UniqueConstraint> getUniqueConstraints() {
        final List<UniqueConstraint> all = new ArrayList<>();
        for (final UniqueConstraint declared : uniqueConstraints) {
            if (spansOnlyUniqueColumns(declared)) {
                all.add(declared);
            }
        }
        for (final TableColumn column : columns) {
            if (column.isUnique() && !coveredByDeclaredConstraint(column.getName())) {
                all.add(new UniqueConstraint(uniqueConstraintName(column.getName()),
                    Collections.singletonList(column.getName())));
            }
        }
        return all;
    }

    /**
     * Only the UNIQUE constraints declared at TABLE level, as stored — for persistence and structural
     * copies, which must not turn a column-level {@code UNIQUE} into a table-level constraint on reload.
     * Callers reporting metadata want {@link #getUniqueConstraints()} instead.
     */
    public List<UniqueConstraint> getDeclaredUniqueConstraints() {
        return new ArrayList<>(uniqueConstraints);
    }

    /** True when every column a declared constraint spans still exists and is still flagged unique. */
    private boolean spansOnlyUniqueColumns(final UniqueConstraint constraint) {
        for (final String columnName : constraint.getColumnNames()) {
            final Integer index = columnIndex.get(columnName.toUpperCase());
            if (index == null || !columns.get(index).isUnique()) {
                return false;
            }
        }
        return true;
    }

    private boolean coveredByDeclaredConstraint(final String columnName) {
        for (final UniqueConstraint declared : uniqueConstraints) {
            if (declared.covers(columnName) && spansOnlyUniqueColumns(declared)) {
                return true;
            }
        }
        return false;
    }

    /** The name of the FOREIGN KEY constraint an inline {@code REFERENCES} on one column declares. */
    public String columnForeignKeyConstraintName(final String columnName) {
        return memoizedConstraintName(columnForeignKeyConstraintNames, columnName);
    }

    private String memoizedConstraintName(final Map<String, String> names, final String columnName) {
        final String key = columnName.toUpperCase();
        String name = names.get(key);
        if (name == null) {
            name = ConstraintNames.generate();
            names.put(key, name);
        }
        return name;
    }

    private boolean hasPrimaryKeyColumn() {
        for (final TableColumn column : columns) {
            if (column.isPrimaryKey()) {
                return true;
            }
        }
        return false;
    }

    /** Forget the generated names tied to one column, so a re-added constraint gets a fresh name. */
    private void forgetColumnConstraintNames(final String columnName) {
        final String key = columnName.toUpperCase();
        uniqueConstraintNames.remove(key);
        columnForeignKeyConstraintNames.remove(key);
    }

    public void addForeignKey(final ForeignKeyConstraint foreignKey) {
        foreignKeys.add(foreignKey);
    }

    public void dropForeignKey(final String constraintName) {
        foreignKeys.removeIf((final var fk) -> fk.getConstraintName().equalsIgnoreCase(constraintName));
    }

    public boolean isTemporary() {
        return isTemporary;
    }

    public boolean isTransient() {
        return isTransient;
    }

    public boolean isHybrid() {
        return hybrid;
    }

    public void setHybrid(final boolean hybrid) {
        this.hybrid = hybrid;
    }

    public long getRowCount() {
        return rowCount;
    }

    public void setRowCount(final long rowCount) {
        this.rowCount = rowCount;
    }

    public List<String> getClusterKeys() {
        return clusterKeys != null ? new ArrayList<>(clusterKeys) : new ArrayList<>();
    }

    public void setClusterKeys(final List<String> clusterKeys) {
        this.clusterKeys = clusterKeys != null ? new ArrayList<>(clusterKeys) : new ArrayList<>();
    }

    public void addColumn(final TableColumn column) {
        String upperName = column.getName().toUpperCase();
        if (columnIndex.containsKey(upperName)) {
            throw new RuntimeException("Column already exists: " + column.getName());
        }
        columnIndex.put(upperName, columns.size());
        columns.add(column);
        if (column.isPrimaryKey()) {
            primaryKeys.add(column.getName());
        }
    }

    public void dropColumn(final String name) {
        Integer index = columnIndex.get(name.toUpperCase());
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.columnDoesNotExist(name));
        }
        TableColumn col = columns.get(index);
        columns.remove((int) index);
        columnIndex.remove(name.toUpperCase());
        primaryKeys.remove(col.getName());
        forgetColumnConstraintNames(col.getName());
        if (!hasPrimaryKeyColumn()) {
            primaryKeyConstraintName = null;
        }

        // Rebuild index
        columnIndex.clear();
        for (int i = 0; i < columns.size(); i++) {
            columnIndex.put(columns.get(i).getName().toUpperCase(), i);
        }

        // A UNIQUE constraint that spanned the dropped column goes with it, whole.
        purgeBrokenUniqueConstraints();
    }

    public void addColumn(final String name, final DataType dataType) {
        addColumn(new TableColumn(name, dataType, true, null, false, false, false));
    }

    public void renameColumn(final String oldName, final String newName) {
        Integer index = columnIndex.get(oldName.toUpperCase());
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Object", oldName));
        }
        if (columnIndex.containsKey(newName.toUpperCase())) {
            throw new RuntimeException("Column already exists: " + newName);
        }

        TableColumn oldColumn = columns.get(index);
        TableColumn newColumn = new TableColumn(newName, oldColumn.getDataType(), oldColumn.isNullable(),
                oldColumn.getDefaultValue(), oldColumn.isPrimaryKey(), oldColumn.isUnique(),
                oldColumn.isAutoIncrement());
        newColumn.setComment(oldColumn.getComment());

        columns.set(index, newColumn);
        columnIndex.remove(oldName.toUpperCase());
        columnIndex.put(newName.toUpperCase(), index);

        // Update primary keys list
        if (oldColumn.isPrimaryKey()) {
            primaryKeys.remove(oldName);
            primaryKeys.add(newName);
        }

        // Renaming a column does not replace its constraints, so their generated names move with it.
        moveConstraintName(uniqueConstraintNames, oldName, newName);
        moveConstraintName(columnForeignKeyConstraintNames, oldName, newName);
        for (final UniqueConstraint declared : uniqueConstraints) {
            declared.renameColumn(oldName, newName);
        }
    }

    private void moveConstraintName(final Map<String, String> names, final String oldName, final String newName) {
        final String existing = names.remove(oldName.toUpperCase());
        if (existing != null) {
            names.put(newName.toUpperCase(), existing);
        }
    }

    public void alterColumnType(final String columnName, final DataType newDataType) {
        Integer index = columnIndex.get(columnName.toUpperCase());
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(columnName));
        }

        TableColumn oldColumn = columns.get(index);
        TableColumn newColumn = new TableColumn(oldColumn.getName(), newDataType, oldColumn.isNullable(),
                oldColumn.getDefaultValue(), oldColumn.isPrimaryKey(), oldColumn.isUnique(),
                oldColumn.isAutoIncrement());
        newColumn.setComment(oldColumn.getComment());
        newColumn.setCollation(oldColumn.getCollation());

        columns.set(index, newColumn);
    }

    public void addPrimaryKeyConstraint(final List<String> columnNames) {
        for (final String colName : columnNames) {
            Integer index = columnIndex.get(colName.toUpperCase());
            if (index == null) {
                throw new RuntimeException(SqlCompilationError.invalidIdentifier(colName));
            }

            TableColumn oldColumn = columns.get(index);
            if (!oldColumn.isPrimaryKey()) {
                columns.set(index, copyColumnWithConstraintFlags(oldColumn, true, oldColumn.isUnique()));
                primaryKeys.add(colName);
            }
        }
    }

    // Rebuild a column with different PRIMARY KEY / UNIQUE flags (both are final on TableColumn), carrying
    // over the rest of its metadata — comment, collation, identity seed/step, foreign-key reference and
    // masking policy — so adding or dropping one constraint never silently discards another property of
    // the column it shares.
    private TableColumn copyColumnWithConstraintFlags(final TableColumn oldColumn,
                                                      final boolean primaryKey, final boolean unique) {
        final TableColumn newColumn = new TableColumn(oldColumn.getName(), oldColumn.getDataType(),
                oldColumn.isNullable(), oldColumn.getDefaultValue(), primaryKey, unique,
                oldColumn.isAutoIncrement(), oldColumn.getIdentityStart(), oldColumn.getIdentityIncrement());
        newColumn.setComment(oldColumn.getComment());
        newColumn.setCollation(oldColumn.getCollation());
        newColumn.setReferencedTable(oldColumn.getReferencedTable());
        newColumn.setReferencedColumn(oldColumn.getReferencedColumn());
        newColumn.setOnDelete(oldColumn.getOnDelete());
        newColumn.setOnUpdate(oldColumn.getOnUpdate());
        newColumn.setRely(oldColumn.getRely());
        newColumn.setMaskingPolicyName(oldColumn.getMaskingPolicyName());
        return newColumn;
    }

    public void dropPrimaryKey() {
        for (int i = 0; i < columns.size(); i++) {
            final TableColumn oldColumn = columns.get(i);
            if (oldColumn.isPrimaryKey()) {
                columns.set(i, copyColumnWithConstraintFlags(oldColumn, false, oldColumn.isUnique()));
            }
        }
        primaryKeys.clear();
        primaryKeyConstraintName = null;   // a re-added PRIMARY KEY is a new constraint, with a new name
    }

    public void dropUnique(final List<String> columnNames) {
        for (final String colName : columnNames) {
            final Integer index = columnIndex.get(colName.toUpperCase());
            if (index == null) {
                throw new RuntimeException(SqlCompilationError.invalidIdentifier(colName));
            }
            clearUniqueFlag(colName);
        }
        purgeBrokenUniqueConstraints();
    }

    /** Clear one column's UNIQUE flag and forget the generated name of its single-column constraint. */
    private void clearUniqueFlag(final String columnName) {
        final Integer index = columnIndex.get(columnName.toUpperCase());
        if (index != null) {
            final TableColumn oldColumn = columns.get(index);
            if (oldColumn.isUnique()) {
                columns.set(index, copyColumnWithConstraintFlags(oldColumn, oldColumn.isPrimaryKey(), false));
            }
        }
        uniqueConstraintNames.remove(columnName.toUpperCase());
    }

    // A UNIQUE constraint is all-or-nothing: once one of its columns stops being unique (dropped, or named
    // in a DROP UNIQUE), the whole constraint is gone and its remaining columns lose the flag with it —
    // otherwise a leftover column would silently become a unique constraint of its own.
    private void purgeBrokenUniqueConstraints() {
        for (int i = uniqueConstraints.size() - 1; i >= 0; i--) {
            final UniqueConstraint declared = uniqueConstraints.get(i);
            if (!spansOnlyUniqueColumns(declared)) {
                uniqueConstraints.remove(i);
                for (final String colName : declared.getColumnNames()) {
                    clearUniqueFlag(colName);
                }
            }
        }
    }

    // Drop the FOREIGN KEY whose column list matches (ALTER TABLE ... DROP FOREIGN KEY (cols)): remove the
    // constraint record and clear the reference metadata on those columns so it no longer surfaces.
    public void dropForeignKeyColumns(final List<String> columnNames) {
        final List<String> target = upperCased(columnNames);
        for (int i = foreignKeys.size() - 1; i >= 0; i--) {
            if (upperCased(foreignKeys.get(i).getColumnNames()).equals(target)) {
                foreignKeys.remove(i);
            }
        }
        for (final String colName : columnNames) {
            final Integer index = columnIndex.get(colName.toUpperCase());
            if (index != null) {
                final TableColumn col = columns.get(index);
                col.setReferencedTable(null);
                col.setReferencedColumn(null);
                columnForeignKeyConstraintNames.remove(colName.toUpperCase());
            }
        }
    }

    private List<String> upperCased(final List<String> names) {
        final List<String> out = new ArrayList<>(names.size());
        for (final String n : names) {
            out.add(n.toUpperCase());
        }
        return out;
    }

    /**
     * Add a UNIQUE constraint spanning one or more columns: flag every column unique (that is how the
     * engine enforces uniqueness) and record the constraint, so its columns stay ONE constraint under ONE
     * name. A null or blank {@code constraintName} auto-names it the way Snowflake does.
     */
    public void addUniqueConstraint(final String constraintName, final List<String> columnNames) {
        addUniqueConstraint(new UniqueConstraint(constraintName, columnNames));
    }

    /** Add an already-built UNIQUE constraint — the CREATE TABLE parse path and snapshot restore. */
    public void addUniqueConstraint(final UniqueConstraint constraint) {
        for (final String colName : constraint.getColumnNames()) {
            final Integer index = columnIndex.get(colName.toUpperCase());
            if (index == null) {
                throw new RuntimeException(SqlCompilationError.invalidIdentifier(colName));
            }

            final TableColumn oldColumn = columns.get(index);
            if (!oldColumn.isUnique()) {
                columns.set(index, copyColumnWithConstraintFlags(oldColumn, oldColumn.isPrimaryKey(), true));
            }
        }
        uniqueConstraints.add(constraint);
    }

    @Override
    public String getObjectType() {
        return "TABLE";
    }

    public String getRowAccessPolicyName() { return rowAccessPolicyName; }
    public void setRowAccessPolicyName(final String name) { this.rowAccessPolicyName = name; }
    public List<String> getRowAccessPolicyColumns() { return new ArrayList<>(rowAccessPolicyColumns); }
    public void setRowAccessPolicyColumns(final List<String> cols) { this.rowAccessPolicyColumns = new ArrayList<>(cols); }
    public boolean hasRowAccessPolicy() { return rowAccessPolicyName != null && !rowAccessPolicyName.isEmpty(); }
}
