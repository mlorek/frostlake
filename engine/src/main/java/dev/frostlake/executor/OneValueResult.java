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

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.math.BigDecimal;
import java.util.Collections;

/**
 * What RESULT_SCAN reads of a block expression that ran as a query of its own: one row of one column, named by the
 * expression as written and typed as its value is.
 */
final class OneValueResult {

    private OneValueResult() {
    }

    /**
     * The result.
     *
     * @param name  the column's name
     * @param value the expression's value
     * @return one row of it
     */
    static ResultSet of(final String name, final Object value) {
        return new ResultSet(Collections.singletonList(new ResultSetColumn(name, typeOf(value))),
            Collections.singletonList(new Row(Collections.<Object>singletonList(value))));
    }

    private static DataType typeOf(final Object value) {
        if (value instanceof Boolean) {
            return BooleanType.BOOLEAN;
        }
        if (value instanceof BigDecimal) {
            return new NumericType("NUMBER", 38, Math.max(0, Math.min(37, ((BigDecimal) value).scale())));
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short) {
            return new NumericType("NUMBER", 38, 0);
        }
        if (value instanceof Double || value instanceof Float) {
            return new NumericType("FLOAT", 38, 9);
        }
        return StringType.VARCHAR;
    }
}
