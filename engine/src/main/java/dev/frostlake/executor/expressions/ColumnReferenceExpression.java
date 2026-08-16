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
    /** N of a {@code $N} positional reference, 0 for a named one. The name still carries the
     *  {@code COLUMN<N>} spelling for the relations that genuinely use it (VALUES, stage files);
     *  the ordinal lets resolution fall back to the Nth column BY POSITION everywhere else,
     *  which is how live reads {@code $N} — by place, not by any name in scope. */
    private final int positionalOrdinal;
    /** Fragment-relative; null when the reference was synthesised rather than parsed. */
    private final SourcePosition position;
    /**
     * The reference EXACTLY as written, part by part — a quoted part keeps its quotes and its case, an
     * unquoted one folds to upper. Live echoes a name it cannot resolve in that form, which is the
     * only form that says what went wrong: {@code "a"} and {@code A} are different columns, so
     * printing the folded name for a quoted reference tells the reader the opposite of the truth.
     * Null when nobody wrote this reference (a star expansion, a synthesised key).
     */
    private String writtenName;

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
        this(tableName, columnName, position, 0);
    }

    /** The {@code $N} flavour — see {@link #getPositionalOrdinal()}. */
    public ColumnReferenceExpression(final String tableName, final String columnName,
                                     final SourcePosition position, final int positionalOrdinal) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.position = position;
        this.positionalOrdinal = positionalOrdinal;
    }

    /** How this reference was written, or null when it was synthesised. */
    public String getWrittenName() {
        return writtenName;
    }

    /** Records the written spelling; see {@link #getWrittenName()}. */
    public void setWrittenName(final String written) {
        this.writtenName = written;
    }

    /** N when this reference was written {@code $N}, 0 when it was written as a name. */
    public int getPositionalOrdinal() {
        return positionalOrdinal;
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
