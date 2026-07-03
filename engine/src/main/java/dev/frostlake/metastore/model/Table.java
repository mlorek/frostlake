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

import dev.frostlake.metastore.SqlObject;
import dev.frostlake.types.DataType;

import java.util.ArrayList;
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
    private long rowCount;
    private List<String> clusterKeys;
    private String rowAccessPolicyName;  // qualified name of attached row access policy, or null
    private List<String> rowAccessPolicyColumns = new ArrayList<>();  // columns passed to policy

    public Table(final String name, final List<TableColumn> columns, final boolean isTemporary) {
        this(name, columns, isTemporary, false);
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

    public TableColumn getColumn(final String name) {
        Integer index = columnIndex.get(name.toUpperCase());
        if (index == null) {
            throw new RuntimeException("Column does not exist: " + name);
        }
        return columns.get(index);
    }

    public int getColumnIndex(final String name) {
        Integer index = columnIndex.get(name.toUpperCase());
        if (index == null) {
            throw new RuntimeException("Column does not exist: " + name);
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
            throw new RuntimeException("Column does not exist: " + name);
        }
        TableColumn col = columns.get(index);
        columns.remove((int) index);
        columnIndex.remove(name.toUpperCase());
        primaryKeys.remove(col.getName());

        // Rebuild index
        columnIndex.clear();
        for (int i = 0; i < columns.size(); i++) {
            columnIndex.put(columns.get(i).getName().toUpperCase(), i);
        }
    }

    public void addColumn(final String name, final DataType dataType) {
        addColumn(new TableColumn(name, dataType, true, null, false, false, false));
    }

    public void renameColumn(final String oldName, final String newName) {
        Integer index = columnIndex.get(oldName.toUpperCase());
        if (index == null) {
            throw new RuntimeException("Column does not exist: " + oldName);
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
    }

    public void alterColumnType(final String columnName, final DataType newDataType) {
        Integer index = columnIndex.get(columnName.toUpperCase());
        if (index == null) {
            throw new RuntimeException("Column does not exist: " + columnName);
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
                throw new RuntimeException("Column does not exist: " + colName);
            }

            TableColumn oldColumn = columns.get(index);
            if (!oldColumn.isPrimaryKey()) {
                TableColumn newColumn = new TableColumn(oldColumn.getName(), oldColumn.getDataType(),
                        oldColumn.isNullable(), oldColumn.getDefaultValue(), true,
                        oldColumn.isUnique(), oldColumn.isAutoIncrement());
                newColumn.setComment(oldColumn.getComment());
                newColumn.setCollation(oldColumn.getCollation());
                columns.set(index, newColumn);
                primaryKeys.add(colName);
            }
        }
    }

    public void addUniqueConstraint(final List<String> columnNames) {
        for (final String colName : columnNames) {
            Integer index = columnIndex.get(colName.toUpperCase());
            if (index == null) {
                throw new RuntimeException("Column does not exist: " + colName);
            }

            TableColumn oldColumn = columns.get(index);
            if (!oldColumn.isUnique()) {
                TableColumn newColumn = new TableColumn(oldColumn.getName(), oldColumn.getDataType(),
                        oldColumn.isNullable(), oldColumn.getDefaultValue(), oldColumn.isPrimaryKey(),
                        true, oldColumn.isAutoIncrement());
                newColumn.setComment(oldColumn.getComment());
                newColumn.setCollation(oldColumn.getCollation());
                columns.set(index, newColumn);
            }
        }
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
