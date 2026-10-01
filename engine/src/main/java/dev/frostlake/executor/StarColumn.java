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

import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.types.DataType;

/**
 * One effective output column produced by expanding a {@code SELECT *} after its EXCLUDE / RENAME / REPLACE /
 * ILIKE modifiers: the value expression to project, the output column name (RENAME changes it), the original
 * source column name (to tell whether it was renamed), and a best-effort output data type.
 */
public final class StarColumn {

    private final String expression;
    private final String outputName;
    private final String sourceName;
    private final DataType dataType;
    private final boolean nullable;
    private final boolean nullabilityKnown;
    private final boolean replaced;
    private final SourcePosition origin;

    public StarColumn(final String expression, final String outputName, final String sourceName,
                      final DataType dataType) {
        this(expression, outputName, sourceName, dataType, true);
    }

    public StarColumn(final String expression, final String outputName, final String sourceName,
                      final DataType dataType, final boolean nullable) {
        this(expression, outputName, sourceName, dataType, nullable, !nullable);
    }

    public StarColumn(final String expression, final String outputName, final String sourceName,
                      final DataType dataType, final boolean nullable, final boolean nullabilityKnown) {
        this(expression, outputName, sourceName, dataType, nullable, nullabilityKnown, null, false);
    }

    public StarColumn(final String expression, final String outputName, final String sourceName,
                      final DataType dataType, final boolean nullable, final boolean nullabilityKnown,
                      final SourcePosition origin, final boolean replaced) {
        this.replaced = replaced;
        this.origin = origin;
        this.expression = expression;
        this.outputName = outputName;
        this.sourceName = sourceName;
        this.dataType = dataType;
        this.nullable = nullable;
        this.nullabilityKnown = nullabilityKnown;
    }

    public String getExpression() {
        return expression;
    }

    public String getOutputName() {
        return outputName;
    }

    public String getSourceName() {
        return sourceName;
    }

    public DataType getDataType() {
        return dataType;
    }

    /** Whether the projected column accepts NULL — false only for a NOT NULL source column that the
     *  star projects as itself, since a REPLACE substitutes an expression. */
    public boolean isNullable() {
        return nullable;
    }

    /** Whether the nullability above was read off a real column rather than defaulted — false for a
     *  REPLACE'd column, which projects an expression. */
    public boolean isNullabilityKnown() {
        return nullabilityKnown;
    }

    /**
     * Where a REPLACE'd column's expression was written, so a refusal inside it is positioned there
     * ({@code SELECT * REPLACE (nosuch AS id)} is "error line 1 at position 18"); null for a column the star
     * projects as itself, which nobody wrote.
     */
    public SourcePosition getOrigin() {
        return origin;
    }

    /** Whether the output name differs from the source column name (RENAME applied). */
    public boolean isRenamed() {
        return !outputName.equalsIgnoreCase(sourceName);
    }

    /** Whether the star's REPLACE substituted an expression of its own for the source column. */
    public boolean isReplaced() {
        return replaced;
    }
}
