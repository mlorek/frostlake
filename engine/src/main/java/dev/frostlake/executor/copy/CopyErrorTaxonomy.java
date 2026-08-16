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
 * The category / code / SQLSTATE triple a {@code VALIDATION_MODE = RETURN_ERRORS} row carries for
 * one rejected record, live-verified per family: a value that would not convert is
 * {@code conversion / 100038 / 22018}; a record whose field count misses the table is
 * {@code parsing / 22000} with code 100080 (short or long record) or 100068 (end of record reached
 * mid-column, the blank-line case); a FILES entry the stage does not hold is
 * {@code other / 100112 / 22000}. Families not yet measured ride the conversion triple.
 */
public final class CopyErrorTaxonomy {

    private final String category;
    private final int code;
    private final String sqlState;

    private CopyErrorTaxonomy(final String category, final int code, final String sqlState) {
        this.category = category;
        this.code = code;
        this.sqlState = sqlState;
    }

    /** The triple for a rejected record, chosen by the failure kind. */
    public static CopyErrorTaxonomy of(final RuntimeException rowError, final String message) {
        if (rowError instanceof CsvColumnCountException) {
            final boolean endOfRecord = message != null && message.startsWith("End of record");
            return new CopyErrorTaxonomy("parsing", endOfRecord ? 100068 : 100080, "22000");
        }
        return new CopyErrorTaxonomy("conversion", 100038, "22018");
    }

    /** The triple for a {@code FILES = (…)} entry the stage does not hold. */
    public static CopyErrorTaxonomy missingFile() {
        return new CopyErrorTaxonomy("other", 100112, "22000");
    }

    public String getCategory() {
        return category;
    }

    public int getCode() {
        return code;
    }

    public String getSqlState() {
        return sqlState;
    }
}
