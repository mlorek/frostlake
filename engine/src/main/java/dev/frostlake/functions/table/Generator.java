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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GENERATOR table function - generates rows with sequential numbers
 * Supports:
 * - ROWCOUNT => n (generates n rows)
 * - TIMELIMIT => seconds (generates rows for specified time period)
 */
public class Generator extends TableFunction {

    /** The most rows one call holds: what a list of them can. */
    private static final long MAX_ROWS = Integer.MAX_VALUE;

    public Generator() {
        super("GENERATOR");
    }

    /** A positional argument is not one GENERATOR can use, and live answers zero rows rather than refuse. */
    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        return execute(new HashMap<String, Object>());
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        validateArgs(namedArgs);
        // A bound that is NULL is a bound not given: ROWCOUNT => NULL generates no rows, and beside a TIMELIMIT
        // it leaves the time to bound the rows. A TIMELIMIT of 0 is no limit either: ROWCOUNT => 3 beside it
        // still generates three rows (live-verified).
        final Long rowCount = boundOf(namedArgs.get("ROWCOUNT"));
        final Long written = boundOf(namedArgs.get("TIMELIMIT"));
        final Long timeLimit = written != null && written.longValue() == 0 ? null : written;
        if (rowCount != null && timeLimit == null && rowCount.longValue() > MAX_ROWS) {
            // The rows are held in memory: a count past what a list holds is refused here rather than cut down.
            throw new RuntimeException("GENERATOR cannot produce " + rowCount + " rows here: at most " + MAX_ROWS
                + " without a TIMELIMIT");
        }

        // Snowflake's GENERATOR produces ZERO columns (live-verified: SELECT * over it fails with
        // "SELECT with no columns") — consumers project literals/expressions over the row count.
        final List<ResultSetColumn> columns = new ArrayList<>();

        final List<Row> rows = new ArrayList<>();

        if (timeLimit == null) {
            if (rowCount != null) {
                // Generate specified number of rows
                for (long i = 0; i < rowCount.longValue(); i++) {
                    rows.add(new Row(new ArrayList<>()));
                }
            }
            return new ResultSet(columns, rows);
        }
        // With a time limit the rows stop at whichever bound is reached first (live-verified).
        final long endTime = System.currentTimeMillis() + timeLimit.longValue() * 1000L;
        long generated = 0;
        while ((rowCount == null || generated < rowCount.longValue()) && generated < MAX_ROWS
                && System.currentTimeMillis() < endTime) {
            rows.add(new Row(new ArrayList<>()));
            generated++;

            // Add small sleep to prevent infinite loop in very short time limits
            if (generated % 1000 == 0) {
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return new ResultSet(columns, rows);
    }

    /**
     * GENERATOR is the permissive end of the table-function family: it IGNORES what it does not
     * recognise instead of refusing it. Live answers zero rows — not an error — for
     * {@code GENERATOR()}, for the positional {@code GENERATOR(3)} and for
     * {@code GENERATOR(NOSUCH =&gt; 3)} alike, so an argument it cannot use simply leaves the row count
     * unset. Only the values of ROWCOUNT and TIMELIMIT are judged: a NULL is a bound not given, a fraction is
     * cut to its whole part, and a negative one is refused, {@code Invalid value [-1] for function 'GENERATOR',
     * parameter ROWCOUNT:  must be non-negative.} (live-verified). A bound of another type, and one that is no
     * constant, are refused before the call is reached (see {@code TableFunctionParameterKind} and
     * {@code GeneratorBounds}).
     */
    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        refuseNegative(boundOf(namedArgs.get("ROWCOUNT")), "ROWCOUNT");
        refuseNegative(boundOf(namedArgs.get("TIMELIMIT")), "TIMELIMIT");
    }

    private static void refuseNegative(final Long bound, final String parameter) {
        if (bound != null && bound.longValue() < 0) {
            throw new RuntimeException(SqlCompilationError.of("Invalid value [" + bound
                + "] for function 'GENERATOR', parameter " + parameter + ":  must be non-negative."));
        }
    }

    /**
     * A bound as the count it is: a number's whole part - one past what a long holds is the longest count
     * there is - a text that spells one, and null for a NULL, which is a bound not given.
     */
    private static Long boundOf(final Object value) {
        if (value instanceof BigDecimal || value instanceof BigInteger) {
            final BigInteger whole = value instanceof BigInteger ? (BigInteger) value
                : ((BigDecimal) value).toBigInteger();
            if (whole.bitLength() >= Long.SIZE) {
                return Long.valueOf(whole.signum() < 0 ? Long.MIN_VALUE : Long.MAX_VALUE);
            }
            return Long.valueOf(whole.longValue());
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        if (value instanceof String) {
            try {
                return Long.valueOf(Long.parseLong((String) value));
            } catch (final NumberFormatException notANumber) {
                throw new RuntimeException("GENERATOR's bound must be a valid number");
            }
        }
        if (value != null) {
            throw new RuntimeException("GENERATOR's bound must be a number");
        }
        return null;
    }

    /** GENERATOR answers no column, whatever its arguments. */
    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return new ArrayList<>();
    }
}
