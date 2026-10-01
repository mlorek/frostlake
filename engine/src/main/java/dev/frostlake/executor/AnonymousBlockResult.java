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

import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;

import java.util.ArrayList;
import java.util.List;

/**
 * The one-row result an anonymous block answers when it runs as a statement — directly, or as the text
 * of an EXECUTE IMMEDIATE: a single column named {@code anonymous block} holding the value its RETURN
 * produced, or NULL typed VARCHAR when the block ran to its end without a RETURN. A CALL answers the
 * same shape with the column named after its procedure, and a RETURN TABLE answers its table instead.
 */
public final class AnonymousBlockResult {

    /** The name Snowflake gives the one column of an anonymous block's result. */
    public static final String COLUMN = "anonymous block";

    /**
     * The type a text result column declares: the unknown 128MB length, whatever the value's own width
     * (live-verified through the driver for a returned literal, a VARCHAR(10) name, a bare FOR counter, a
     * block with no RETURN and a CALL of a RETURNS VARCHAR(10) procedure alike).
     */
    public static final DataType TEXT = new StringType("VARCHAR", StringResultWidths.UNBOUNDED);

    private AnonymousBlockResult() {
    }

    /**
     * The result of a block whose RETURN produced {@code value}.
     *
     * @param value the returned value, or null
     * @param type the type the result column declares
     * @return the single-row, single-column result
     */
    public static ResultSet of(final Object value, final DataType type) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(COLUMN, type, null));
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(value));
        return new ResultSet(columns, rows);
    }

    /**
     * The result of a block that ran to its end without a RETURN.
     *
     * @return one row holding NULL, typed as a text
     */
    public static ResultSet withoutReturn() {
        return of(null, TEXT);
    }

    /**
     * The type the result column declares for a value of {@code type}: a text at the unknown 128MB length
     * and a binary unsized, whatever width was written (live-verified: a VARCHAR(10) value reads
     * VARCHAR(134217728) through the driver and in SYSTEM$TYPEOF over its RESULT_SCAN, and a table created
     * over that scan stores VARCHAR(16777216); a BINARY(10) stores BINARY(8388608)); any other type as it is.
     *
     * @param type the value's type, or null
     * @return the column's type, or null when there is none
     */
    public static DataType columnType(final DataType type) {
        if (type instanceof StringType && !(type instanceof UuidType)) {
            return TEXT;
        }
        return type instanceof BinaryType ? BinaryType.UNSIZED : type;
    }

    /**
     * Whether a statement is an anonymous block — {@code [DECLARE …] BEGIN … END} — in its own right.
     *
     * @param statement the statement's parse tree, or null
     * @return true for a block statement
     */
    public static boolean isBlock(final FrostlakeParser.StatementContext statement) {
        return statement != null && statement.proceduralStatement() != null
            && statement.proceduralStatement().beginEndBlock() != null;
    }
}
