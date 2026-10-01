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

import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.List;

/**
 * What a procedure declared RETURNS TABLE answers to a CALL: the table its RETURN TABLE hands back, under the
 * declared columns' names and types with the returned values unconverted. A procedure declaring no columns,
 * RETURNS TABLE (), answers the table as returned. Every rule is live-verified.
 */
final class ReturnedTable {

    /** The refusal of a returned table whose shape the declaration does not take. */
    static final String MISMATCH =
        "Stored procedure execution error: data type of returned table does not match expected returned table type";

    /** The refusal of a table procedure whose body ran to its end without a RETURN. */
    static final String MISSING_RETURN = "SQL compilation error: stored procedure is missing a return statement";

    private ReturnedTable() {
    }

    /**
     * The CALL result of a table the body returned. It must have as many columns as the declaration, each of
     * the declared type's family; a text or a binary of any width, and a number of any precision and scale
     * that is exact where the declared one is, count as the declared type. The rows are kept as returned.
     *
     * @param declared the procedure's declared columns, none for RETURNS TABLE ()
     * @param returned the table the body returned
     * @return the CALL's result
     */
    static ResultSet shape(final List<Parameter> declared, final ResultSet returned) {
        if (declared.isEmpty()) {
            return returned;
        }
        final List<ResultSetColumn> actual = returned.getColumns();
        if (actual.size() != declared.size()) {
            throw new RuntimeException(MISMATCH);
        }
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (int i = 0; i < declared.size(); i++) {
            if (!takes(declared.get(i).getDataType(), actual.get(i).getDataType())) {
                throw new RuntimeException(MISMATCH);
            }
            columns.add(new ResultSetColumn(declared.get(i).getName(), declared.get(i).getDataType(), null));
        }
        return new ResultSet(columns, returned.getRows());
    }

    /**
     * Whether a returned column of type {@code actual} fills a declared column of type {@code declared}: an
     * exact number fills an exact number and an approximate one an approximate one; a text fills a text and
     * a binary a binary, whatever their widths; a date or time fills its own kind at the same fractional
     * precision; any other type fills only itself — a VARIANT holding an object does not fill an OBJECT, nor
     * an OBJECT a VARIANT.
     */
    private static boolean takes(final DataType declared, final DataType actual) {
        if (declared == null || actual == null) {
            return false;
        }
        if (declared instanceof NumericType && actual instanceof NumericType) {
            return NumericType.isApproximate(declared) == NumericType.isApproximate(actual);
        }
        if (declared instanceof StringType || declared instanceof BinaryType) {
            return declared.getClass() == actual.getClass();
        }
        if (declared instanceof DateTimeType && actual instanceof DateTimeType) {
            return declared.getName().equals(actual.getName())
                && ((DateTimeType) declared).getPrecision() == ((DateTimeType) actual).getPrecision();
        }
        return declared.getClass() == actual.getClass();
    }
}
