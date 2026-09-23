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

import dev.frostlake.types.DataType;

/**
 * How a positional reference reads a staged-file query ({@code FROM @stage}): {@code $1} to {@code $fields}
 * read the files' fields, and every position past them up to {@link #positions()} reads NULL, typed as the
 * fields are. The METADATA$FILENAME and METADATA$FILE_ROW_NUMBER columns the query carries after its fields
 * answer by name only, never by position. A CSV query over a named or user stage reads 4096 positions:
 * {@code $4096} is NULL over a two-field file, and {@code $4097} is {@code column '$4097' does not exist}. A
 * table stage reads as many positions as its table has columns, and a position past them is an invalid
 * identifier.
 */
public final class StagePositions {

    /** The positions a CSV query over a named or user stage reads. */
    public static final int NAMED_STAGE_POSITIONS = 4096;

    private final int fields;
    private final int positions;
    private final DataType fieldType;
    private final boolean missingPastPositions;

    /**
     * @param fields               how many fields the query's files hold: its leading columns
     * @param positions            how many positions a reference reads; never fewer than the fields
     * @param fieldType            the type every position reads as
     * @param missingPastPositions whether a position past them names a column that does not exist, rather
     *                             than an invalid identifier
     */
    public StagePositions(final int fields, final int positions, final DataType fieldType,
                          final boolean missingPastPositions) {
        this.fields = fields;
        this.positions = Math.max(fields, positions);
        this.fieldType = fieldType;
        this.missingPastPositions = missingPastPositions;
    }

    /** @return how many leading columns of the query are the files' fields */
    public int fields() {
        return fields;
    }

    /** @return how many positions a reference reads */
    public int positions() {
        return positions;
    }

    /** @return the type every position reads as */
    public DataType fieldType() {
        return fieldType;
    }

    /** @return whether a position past {@link #positions()} names a column that does not exist, rather than an
     *  invalid identifier */
    public boolean missingPastPositions() {
        return missingPastPositions;
    }
}
