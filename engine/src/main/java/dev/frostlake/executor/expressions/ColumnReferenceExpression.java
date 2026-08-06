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

package dev.frostlake.executor.expressions;

/**
 * Represents a column reference (e.g., "name", "users.id", "u.name")
 */
public class ColumnReferenceExpression implements Expression {
    private final String tableName;  // null if unqualified
    private final String columnName;
    /** Fragment-relative; null when the reference was synthesised rather than parsed. */
    private final SourcePosition position;

    public ColumnReferenceExpression(final String columnName) {
        this(null, columnName, null);
    }

    public ColumnReferenceExpression(final String tableName, final String columnName) {
        this(tableName, columnName, null);
    }

    /**
     * A reference that remembers where it sat in the text it was parsed from, so a message about it
     * can carry the position Snowflake always reports. See {@link SourcePosition} for why the place
     * is fragment-relative and not absolute.
     */
    public ColumnReferenceExpression(final String tableName, final String columnName,
                                     final SourcePosition position) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.position = position;
    }

    /** Where this reference sat in its fragment, or null when nobody wrote it. */
    public SourcePosition getPosition() {
        return position;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public boolean isQualified() {
        return tableName != null;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitColumnReference(this);
    }

    @Override
    public String toString() {
        if (tableName != null) {
            return tableName + "." + columnName;
        }
        return columnName;
    }
}
