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

    public ColumnReferenceExpression(final String columnName) {
        this.tableName = null;
        this.columnName = columnName;
    }

    public ColumnReferenceExpression(final String tableName, final String columnName) {
        this.tableName = tableName;
        this.columnName = columnName;
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
