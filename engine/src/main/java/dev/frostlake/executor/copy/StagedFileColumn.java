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

package dev.frostlake.executor.copy;

/**
 * One top-level column a self-describing staged file declares — as {@link StageFileReader#readColumns} hands it
 * to INFER_SCHEMA: its name, the type the account infers for it, and whether the file lets it be NULL.
 */
public final class StagedFileColumn {

    private final String name;
    private final String typeText;
    private final boolean nullable;

    /**
     * A declared column.
     *
     * @param name the column's name, as the file spells it
     * @param typeText the inferred type, spelled as the account reports it ({@code NUMBER(38, 0)}, {@code TEXT})
     * @param nullable whether the file lets the column be NULL
     */
    public StagedFileColumn(final String name, final String typeText, final boolean nullable) {
        this.name = name;
        this.typeText = typeText;
        this.nullable = nullable;
    }

    /** The column's name, as the file spells it. */
    public String name() {
        return name;
    }

    /** The inferred type, spelled as the account reports it. */
    public String typeText() {
        return typeText;
    }

    /** Whether the file lets the column be NULL. */
    public boolean nullable() {
        return nullable;
    }
}
