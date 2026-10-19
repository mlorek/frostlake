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

package dev.frostlake.functions.table;

import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.List;

/**
 * The columns an INFORMATION_SCHEMA listing function answers, each declaring the type the plan gives it before any
 * row is read, so that SYSTEM$TYPEOF and the type rules read it: an unsized text is a VARCHAR of no length, as
 * {@code VARCHAR[LOB]}, and every other column its own type, {@code NUMBER(38,0)[SB16]} (live-verified).
 */
final class DeclaredColumnTypes {

    private DeclaredColumnTypes() {
    }

    /**
     * The columns, each declaring its type; a column that declares one already is kept as it is.
     *
     * @param columns the columns as the function lists them
     * @return the columns, typed
     */
    static List<ResultSetColumn> typed(final List<ResultSetColumn> columns) {
        final List<ResultSetColumn> typed = new ArrayList<>(columns.size());
        for (final ResultSetColumn column : columns) {
            if (column.getStaticType() != null) {
                typed.add(column);
                continue;
            }
            final DataType type = column.getDataType();
            final boolean unsized = type instanceof StringType
                && ((StringType) type).getMaxLength() == DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR;
            typed.add(new ResultSetColumn(column.getName(), type, column.getTableName(),
                unsized ? new LengthlessStringType() : type));
        }
        return typed;
    }
}
