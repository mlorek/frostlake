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
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SPLIT_TO_TABLE table function - splits a string into multiple rows
 * Supports:
 * - STRING parameter (the string to split)
 * - DELIMITER parameter (the delimiter to split on)
 * Returns rows with SEQ (0-based index), INDEX (1-based index), and VALUE columns
 */
public class SplitToTable extends TableFunction {

    public SplitToTable() {
        super("SPLIT_TO_TABLE");
    }

    /** Positional form {@code SPLIT_TO_TABLE(string [, delimiter])} — mapped onto the named arguments. */
    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        final Map<String, Object> namedArgs = new HashMap<>();
        if (!positionalArgs.isEmpty()) {
            namedArgs.put("STRING", positionalArgs.get(0));
        }
        if (positionalArgs.size() > 1) {
            namedArgs.put("DELIMITER", positionalArgs.get(1));
        }
        return execute(namedArgs);
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        validateArgs(namedArgs);

        // Get string parameter
        String inputString = "";
        if (namedArgs.containsKey("STRING")) {
            Object value = namedArgs.get("STRING");
            if (value != null) {
                inputString = value.toString();
            }
        }

        // Get delimiter parameter (default to comma if not specified)
        String delimiter = ",";
        if (namedArgs.containsKey("DELIMITER")) {
            Object value = namedArgs.get("DELIMITER");
            if (value != null) {
                delimiter = value.toString();
            }
        }

        // Create result set with SEQ, INDEX, and VALUE columns
        List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("SEQ", NumericType.INTEGER));
        columns.add(new ResultSetColumn("INDEX", NumericType.INTEGER));
        columns.add(new ResultSetColumn("VALUE", StringType.VARCHAR));

        List<Row> rows = new ArrayList<>();

        // Split the string
        if (inputString == null || inputString.isEmpty()) {
            // Empty string results in one row with empty value
            List<Object> values = new ArrayList<>();
            values.add(0L);  // SEQ (0-based)
            values.add(1L);  // INDEX (1-based)
            values.add("");  // VALUE
            rows.add(new Row(values));
        } else {
            String[] parts;
            if (delimiter.isEmpty()) {
                // Empty delimiter means split into individual characters
                parts = inputString.split("");
            } else {
                // Use the delimiter for splitting
                // Use -1 as limit to include trailing empty strings
                parts = inputString.split(Pattern.quote(delimiter), -1);
            }

            // Generate rows for each part
            for (int i = 0; i < parts.length; i++) {
                List<Object> values = new ArrayList<>();
                values.add((long) i);      // SEQ (0-based)
                values.add((long) (i + 1)); // INDEX (1-based)
                values.add(parts[i]);       // VALUE
                rows.add(new Row(values));
            }
        }

        return new ResultSet(columns, rows);
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        if (namedArgs.isEmpty()) {
            throw new RuntimeException("SPLIT_TO_TABLE function requires at least one argument (STRING)");
        }

        // Check for valid argument names
        for (final String key : namedArgs.keySet()) {
            String upperKey = key.toUpperCase();
            if (!upperKey.equals("STRING") && !upperKey.equals("DELIMITER")) {
                throw new RuntimeException("Invalid argument for SPLIT_TO_TABLE: " + key +
                    ". Valid arguments are STRING and DELIMITER");
            }
        }

        // STRING parameter is required
        if (!namedArgs.containsKey("STRING")) {
            throw new RuntimeException("SPLIT_TO_TABLE function requires STRING argument");
        }
    }
}
