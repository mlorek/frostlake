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

import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BinaryWidthSpelling;
import dev.frostlake.types.DataType;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;

/**
 * The types of the columns a routine's RETURNS TABLE declares, and of the result columns they type
 * (live-verified). A text or a binary column written with no width is of no width of its own, where a table's
 * column of the same spelling takes the default one:
 *
 * <pre>
 *                                    a driver reads       SYSTEM$TYPEOF        a table created over it
 *   a VARCHAR   a procedure's        VARCHAR(134217728)   VARCHAR(134217728)   VARCHAR(16777216)
 *               a table function's   VARCHAR(134217728)   VARCHAR              VARCHAR(16777216)
 *   a BINARY    a procedure's        BINARY(67108864)     BINARY(67108864)     BINARY(8388608)
 *               a table function's   BINARY(67108864)     BINARY               BINARY(8388608)
 * </pre>
 *
 * <p>A width that is written is kept wherever the column shows, the default one included: a column declared
 * {@code VARCHAR(16777216)} reads 16777216 and one declared {@code BINARY(8388608)} 8388608. STRING, TEXT and the
 * other spellings of a text of no width read as VARCHAR does, and CHAR, whose default width is one, keeps it.
 * The routine's DDL writes each column back as it was declared, while its description, its listing and
 * INFORMATION_SCHEMA name the type alone.
 */
public final class ReturnColumnTypes {

    private ReturnColumnTypes() {
    }

    /**
     * The type a RETURNS TABLE column declares.
     *
     * @param parsed       the type as a table's column of that spelling would have it
     * @param widthWritten whether the declaration wrote a width
     * @return the declared type: a text or a binary of no width when none was written
     */
    public static DataType declared(final DataType parsed, final boolean widthWritten) {
        if (widthWritten) {
            return parsed;
        }
        if (parsed instanceof StringType && !(parsed instanceof UuidType)
                && ((StringType) parsed).getMaxLength() == SqlTypeNames.DESCRIBED_STRING_MAXIMUM) {
            return new LengthlessStringType();
        }
        if (parsed instanceof BinaryType) {
            return new BinaryType(BinaryType.DEFAULT_WIDTH, ((BinaryType) parsed).isFixed(),
                BinaryWidthSpelling.UNSIZED);
        }
        return parsed;
    }

    /**
     * A declared column's type as the routine's DDL writes it back: bare where no width was written, and with
     * its width where one was — {@code VARCHAR} beside {@code VARCHAR(16777216)}, {@code BINARY} beside
     * {@code BINARY(8388608)}.
     *
     * @param declared the declared column's type
     * @return the spelling
     */
    public static String ddlText(final DataType declared) {
        if (declared instanceof LengthlessStringType) {
            return "VARCHAR";
        }
        if (declared instanceof BinaryType
                && ((BinaryType) declared).getWidthSpelling() == BinaryWidthSpelling.UNSIZED) {
            return "BINARY";
        }
        return SqlTypeNames.canonical(declared);
    }

    /**
     * The type a procedure's result column has for a declared column: a text or a binary of no width at the
     * widest a value can be, sized. A table function's result column keeps the declared type as it is.
     *
     * @param declared the declared column's type
     * @return the result column's type
     */
    static DataType ofProcedure(final DataType declared) {
        if (declared instanceof LengthlessStringType) {
            return AnonymousBlockResult.TEXT;
        }
        if (declared instanceof BinaryType
                && ((BinaryType) declared).getWidthSpelling() == BinaryWidthSpelling.UNSIZED) {
            return BinaryType.AT_MAXIMUM;
        }
        return declared;
    }
}
