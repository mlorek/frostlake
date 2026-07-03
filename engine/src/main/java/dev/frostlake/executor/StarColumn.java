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

    public StarColumn(final String expression, final String outputName, final String sourceName,
                      final DataType dataType) {
        this.expression = expression;
        this.outputName = outputName;
        this.sourceName = sourceName;
        this.dataType = dataType;
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

    /** Whether the output name differs from the source column name (RENAME applied). */
    public boolean isRenamed() {
        return !outputName.equalsIgnoreCase(sourceName);
    }
}
