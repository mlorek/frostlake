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

import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

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

        // Get rowcount parameter
        Integer rowCount = null;
        if (namedArgs.containsKey("ROWCOUNT")) {
            final Object value = namedArgs.get("ROWCOUNT");
            if (value instanceof Number) {
                rowCount = ((Number) value).intValue();
            } else if (value instanceof String) {
                rowCount = Integer.parseInt((String) value);
            }
        }

        // Get timelimit parameter (in seconds)
        Integer timeLimit = null;
        if (namedArgs.containsKey("TIMELIMIT")) {
            final Object value = namedArgs.get("TIMELIMIT");
            if (value instanceof Number) {
                timeLimit = ((Number) value).intValue();
            } else if (value instanceof String) {
                timeLimit = Integer.parseInt((String) value);
            }
        }

        // Snowflake's GENERATOR produces ZERO columns (live-verified: SELECT * over it fails with
        // "SELECT with no columns") — consumers project literals/expressions over the row count.
        final List<ResultSetColumn> columns = new ArrayList<>();

        final List<Row> rows = new ArrayList<>();

        if (rowCount != null) {
            // Generate specified number of rows
            for (int i = 0; i < rowCount; i++) {
                rows.add(new Row(new ArrayList<>()));
            }
        } else if (timeLimit != null) {
            // Generate rows for specified time period
            final long startTime = System.currentTimeMillis();
            final long endTime = startTime + (timeLimit * 1000L);
            int generated = 0;

            while (System.currentTimeMillis() < endTime) {
                rows.add(new Row(new ArrayList<>()));
                generated++;

                // Add small sleep to prevent infinite loop in very short time limits
                if (timeLimit > 0 && generated % 1000 == 0) {
                    try {
                        Thread.sleep(1);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
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
     * unset. Only the values of ROWCOUNT and TIMELIMIT are judged.
     */
    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Validate ROWCOUNT
        if (namedArgs.containsKey("ROWCOUNT")) {
            final Object value = namedArgs.get("ROWCOUNT");
            try {
                final int rowCount;
                if (value instanceof Number) {
                    rowCount = ((Number) value).intValue();
                } else if (value instanceof String) {
                    rowCount = Integer.parseInt((String) value);
                } else {
                    throw new RuntimeException("ROWCOUNT must be a number");
                }
                if (rowCount < 0) {
                    throw new RuntimeException("ROWCOUNT must be non-negative");
                }
            } catch (final NumberFormatException e) {
                throw new RuntimeException("ROWCOUNT must be a valid number");
            }
        }

        // Validate TIMELIMIT
        if (namedArgs.containsKey("TIMELIMIT")) {
            final Object value = namedArgs.get("TIMELIMIT");
            try {
                final int timeLimit;
                if (value instanceof Number) {
                    timeLimit = ((Number) value).intValue();
                } else if (value instanceof String) {
                    timeLimit = Integer.parseInt((String) value);
                } else {
                    throw new RuntimeException("TIMELIMIT must be a number");
                }
                if (timeLimit < 0) {
                    throw new RuntimeException("TIMELIMIT must be non-negative");
                }
            } catch (final NumberFormatException e) {
                throw new RuntimeException("TIMELIMIT must be a valid number");
            }
        }
    }
}
