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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The refusal of a write that would give one target row two values: an UPDATE joining a row to several source
 * rows under ERROR_ON_NONDETERMINISTIC_UPDATE, or a MERGE updating one under ERROR_ON_NONDETERMINISTIC_MERGE.
 * The message is the account's two lines — the sentence, then the target row as it stood before the statement:
 *
 * <pre>
 *   Duplicate row detected during DML action
 *   Row Values: [1, "x", 19724, 1, 250, NULL]
 * </pre>
 *
 * <p>The row is printed as it is stored rather than as a query shows it: a text in double quotes with nothing
 * escaped, a fixed-point number by its digits without the point (2.50 as 250), a date as its days since 1970, a
 * timestamp without a zone as its nanoseconds since 1970, a boolean as 1 or 0 and a NULL as the word
 * (live-verified).
 */
public final class DuplicateRowRefusal {

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    private DuplicateRowRefusal() {
    }

    /**
     * The refusal naming one target row.
     *
     * @param target the table written to
     * @param row    the target row several source rows joined
     * @return the exception to throw
     */
    public static RuntimeException of(final Table target, final Row row) {
        return new RuntimeException("Duplicate row detected during DML action\nRow Values: " + values(target, row));
    }

    private static String values(final Table target, final Row row) {
        final List<TableColumn> columns = target.getColumns();
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < row.getValues().size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(stored(row.getValues().get(i), i < columns.size() ? columns.get(i).getDataType() : null));
        }
        return text.append(']').toString();
    }

    private static String stored(final Object value, final DataType type) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "1" : "0";
        }
        if (value instanceof LocalDate) {
            return String.valueOf(((LocalDate) value).toEpochDay());
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime instant = (LocalDateTime) value;
            return BigInteger.valueOf(instant.toEpochSecond(ZoneOffset.UTC)).multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(instant.getNano())).toString();
        }
        if (value instanceof Number) {
            if (type instanceof NumericType && !NumericType.isApproximate(type)
                    && !(value instanceof Double) && !(value instanceof Float)) {
                return new BigDecimal(value.toString()).movePointRight(((NumericType) type).getScale())
                    .toBigInteger().toString();
            }
            return String.valueOf(value);
        }
        if (type instanceof BooleanType) {
            return String.valueOf(value);
        }
        return "\"" + value + "\"";
    }
}
