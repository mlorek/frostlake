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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.*;

/**
 * FLATTEN table function - flattens semi-structured data into rows
 * Supports VARIANT, OBJECT, and ARRAY types
 *
 * Returns columns:
 * - SEQ: sequence number (0-based)
 * - KEY: key name for objects, null for arrays
 * - PATH: path to the element
 * - INDEX: array index for arrays, null for objects
 * - VALUE: the flattened value
 * - THIS: the original input value
 */
public class Flatten extends TableFunction {

    private static final ObjectMapper JACKSON = new ObjectMapper();

    public Flatten() {
        super("FLATTEN");
    }

    /** Positional form {@code FLATTEN(input)} — the first argument is INPUT. */
    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        final Map<String, Object> namedArgs = new HashMap<>();
        if (!positionalArgs.isEmpty()) {
            namedArgs.put("INPUT", positionalArgs.get(0));
        }
        return execute(namedArgs);
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        validateArgs(namedArgs);

        // Get INPUT parameter (required). A present-but-NULL input is NOT an error in Snowflake: NULL is
        // simply not expandable, so the row contributes no output (OUTER => FALSE, the default) or a single
        // all-NULL row (OUTER => TRUE). Only an ABSENT INPUT argument is an error, which validateArgs()
        // above already rejects. Throwing here aborted the WHOLE statement — the table function is expanded
        // for every left row before WHERE can filter, so one NULL in a scanned column killed the query.
        Object input = namedArgs.get("INPUT");

        // Get optional parameters
        String path = namedArgs.containsKey("PATH") ? namedArgs.get("PATH").toString() : null;
        boolean outer = namedArgs.containsKey("OUTER") &&
                       Boolean.parseBoolean(namedArgs.get("OUTER").toString());
        boolean recursive = namedArgs.containsKey("RECURSIVE") &&
                           Boolean.parseBoolean(namedArgs.get("RECURSIVE").toString());
        final FlattenMode mode = namedArgs.containsKey("MODE")
                ? FlattenMode.fromString(namedArgs.get("MODE").toString()) : FlattenMode.BOTH;

        // Create result columns
        List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("SEQ", NumericType.INTEGER));
        columns.add(new ResultSetColumn("KEY", StringType.VARCHAR));
        columns.add(new ResultSetColumn("PATH", StringType.VARCHAR));
        columns.add(new ResultSetColumn("INDEX", NumericType.INTEGER));
        columns.add(new ResultSetColumn("VALUE", StringType.VARCHAR));
        columns.add(new ResultSetColumn("THIS", StringType.VARCHAR));

        List<Row> rows = new ArrayList<>();

        // Parse input as JSON
        JsonNode jsonInput = parseInput(input);
        String inputStr = input == null ? null : input.toString();

        // Apply path filter if specified
        if (path != null && !path.isEmpty()) {
            jsonInput = navigatePath(jsonInput, path);
        }

        // Flatten the JSON structure
        flattenElement(jsonInput, "", inputStr, rows, 0, recursive, mode, outer);

        // If outer is true and no rows were generated, add a single null row
        if (outer && rows.isEmpty()) {
            List<Object> values = new ArrayList<>();
            values.add(0L);      // SEQ
            values.add(null);    // KEY
            values.add(null);    // PATH
            values.add(null);    // INDEX
            values.add(null);    // VALUE
            values.add(inputStr); // THIS
            rows.add(new Row(values));
        }

        return new ResultSet(columns, rows);
    }

    private JsonNode parseInput(final Object input) {
        if (input == null) {
            return null;
        }

        String inputStr = input.toString();

        // Try to parse as JSON
        try {
            return JACKSON.readTree(inputStr);
        } catch (final Exception e) {
            // If not valid JSON, treat as string
            return new StringNode(inputStr);
        }
    }

    private JsonNode navigatePath(final JsonNode element, final String path) {
        if (path == null || path.isEmpty() || element == null) {
            return element;
        }

        // Simple path navigation (e.g., "field1.field2" or "array[0]")
        String[] parts = path.split("\\.");
        JsonNode current = element;

        for (final String part : parts) {
            if (current == null || current.isNull()) {
                return null;
            }

            // Handle array index
            if (part.matches(".*\\[\\d+\\]")) {
                String fieldName = part.substring(0, part.indexOf('['));
                int index = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));

                if (!fieldName.isEmpty() && current.isObject()) {
                    current = current.get(fieldName);
                }

                if (current != null && current.isArray()) {
                    if (index >= 0 && index < current.size()) {
                        current = current.get(index);
                    } else {
                        return null;
                    }
                }
            } else if (current.isObject()) {
                current = current.get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    private int flattenElement(final JsonNode element, final String currentPath, final String thisValue,
                               final List<Row> rows, final int seqStart, final boolean recursive,
                               final FlattenMode mode, final boolean outer) {
        int seq = seqStart;

        if (element == null || element.isNull()) {
            return seq;
        }

        if (element.isObject()) {
            if (element.size() == 0 && outer) {
                // Empty object with outer=true
                addRow(rows, seq++, null, currentPath, null, null, thisValue);
            } else {
                Set<Map.Entry<String, JsonNode>> fields = element.properties();
                for(final Map.Entry<String, JsonNode> entry : fields) {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    String newPath = currentPath.isEmpty() ? key : currentPath + "." + key;

                    if (mode == FlattenMode.OBJECT || mode == FlattenMode.BOTH) {
                        addRow(rows, seq++, key, newPath, null, nodeToValue(value), thisValue);
                    }

                    // Recursive flattening
                    if (recursive && (value.isObject() || value.isArray())) {
                        seq = flattenElement(value, newPath, thisValue, rows, seq, true, mode, outer);
                    }
                }
            }
        } else if (element.isArray()) {
            if (element.size() == 0 && outer) {
                // Empty array with outer=true
                addRow(rows, seq++, null, currentPath, null, null, thisValue);
            } else {
                for (int i = 0; i < element.size(); i++) {
                    JsonNode value = element.get(i);
                    String newPath = currentPath + "[" + i + "]";

                    if (mode == FlattenMode.ARRAY || mode == FlattenMode.BOTH) {
                        addRow(rows, seq++, null, newPath, (long) i, nodeToValue(value), thisValue);
                    }

                    // Recursive flattening
                    if (recursive && (value.isObject() || value.isArray())) {
                        seq = flattenElement(value, newPath, thisValue, rows, seq, true, mode, outer);
                    }
                }
            }
        } else {
            // Primitive value
            addRow(rows, seq++, null, currentPath, null, nodeToValue(element), thisValue);
        }

        return seq;
    }

    private void addRow(final List<Row> rows, final int seq, final String key, final String path, final Long index,
                       final Object value, final String thisValue) {
        List<Object> values = new ArrayList<>();
        values.add((long) seq);
        values.add(key);
        values.add(path.isEmpty() ? null : path);
        values.add(index);
        values.add(value);
        values.add(thisValue);
        rows.add(new Row(values));
    }

    /**
     * The VALUE column keeps a scalar node's TYPE — a JSON boolean/number element stays a Boolean/Number
     * (Snowflake's FLATTEN value is a typed VARIANT), so re-aggregating it (OBJECT_AGG, ARRAY_AGG,
     * OBJECT_CONSTRUCT) does not turn {@code true} into the string {@code "true"}. Textual nodes unwrap to
     * their text; objects/arrays stay JSON text for path access.
     */
    private Object nodeToValue(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            // Same disambiguation as path extraction: a string element whose content looks like JSON
            // structure keeps its quoted form so it is not mistaken for a real array/object downstream.
            final String text = node.asText();
            final String trimmedText = text.trim();
            if (trimmedText.startsWith("[") || trimmedText.startsWith("{")) {
                return node.toString();
            }
            return text;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isLong() || node.isInt()) {
            return node.asLong();
        }
        if (node.isBigInteger() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        return node.toString();
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        if (namedArgs.isEmpty()) {
            throw new RuntimeException("FLATTEN function requires INPUT argument");
        }

        // Validate INPUT parameter is present
        if (!namedArgs.containsKey("INPUT")) {
            throw new RuntimeException("FLATTEN function requires INPUT argument");
        }

        // Check for valid argument names
        for (final String key : namedArgs.keySet()) {
            String upperKey = key.toUpperCase();
            if (!upperKey.equals("INPUT") && !upperKey.equals("PATH") &&
                !upperKey.equals("OUTER") && !upperKey.equals("RECURSIVE") &&
                !upperKey.equals("MODE")) {
                throw new RuntimeException("Invalid argument for FLATTEN: " + key +
                    ". Valid arguments are INPUT, PATH, OUTER, RECURSIVE, MODE");
            }
        }

        // Validate MODE if present
        if (namedArgs.containsKey("MODE")
                && FlattenMode.fromString(namedArgs.get("MODE").toString()) == null) {
            throw new RuntimeException("MODE must be OBJECT, ARRAY, or BOTH");
        }
    }
}
