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

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        validateArgs(namedArgs);

        // Get rowcount parameter
        Integer rowCount = null;
        if (namedArgs.containsKey("ROWCOUNT")) {
            Object value = namedArgs.get("ROWCOUNT");
            if (value instanceof Number) {
                rowCount = ((Number) value).intValue();
            } else if (value instanceof String) {
                rowCount = Integer.parseInt((String) value);
            }
        }

        // Get timelimit parameter (in seconds)
        Integer timeLimit = null;
        if (namedArgs.containsKey("TIMELIMIT")) {
            Object value = namedArgs.get("TIMELIMIT");
            if (value instanceof Number) {
                timeLimit = ((Number) value).intValue();
            } else if (value instanceof String) {
                timeLimit = Integer.parseInt((String) value);
            }
        }

        // Snowflake's GENERATOR produces ZERO columns (live-verified: SELECT * over it fails with
        // "SELECT with no columns") — consumers project literals/expressions over the row count.
        List<ResultSetColumn> columns = new ArrayList<>();

        List<Row> rows = new ArrayList<>();

        if (rowCount != null) {
            // Generate specified number of rows
            for (int i = 0; i < rowCount; i++) {
                rows.add(new Row(new ArrayList<>()));
            }
        } else if (timeLimit != null) {
            // Generate rows for specified time period
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (timeLimit * 1000L);
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

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        if (namedArgs.isEmpty()) {
            throw new RuntimeException("GENERATOR function requires at least one argument (ROWCOUNT or TIMELIMIT)");
        }

        // Check for valid argument names
        for (final String key : namedArgs.keySet()) {
            String upperKey = key.toUpperCase();
            if (!upperKey.equals("ROWCOUNT") && !upperKey.equals("TIMELIMIT")) {
                throw new RuntimeException("Invalid argument for GENERATOR: " + key +
                    ". Valid arguments are ROWCOUNT and TIMELIMIT");
            }
        }

        // Validate ROWCOUNT
        if (namedArgs.containsKey("ROWCOUNT")) {
            Object value = namedArgs.get("ROWCOUNT");
            try {
                int rowCount;
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
            Object value = namedArgs.get("TIMELIMIT");
            try {
                int timeLimit;
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
